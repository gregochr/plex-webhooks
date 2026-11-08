package gregochr.services.events;

import gregochr.support.FakeDatabaseReadyListener;
import gregochr.support.LogCapture;
import gregochr.support.MutableClock;
import gregochr.support.TestClocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.scheduling.TaskScheduler;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("Playback Database Lifecycle Tests")
public class PlaybackDatabaseLifecycleTest {

    private final List<Runnable> scheduledTasks = new ArrayList<>();
    private final List<ScheduledFuture<?>> futures = new ArrayList<>();
    private final List<String> calls = new ArrayList<>();
    private final MutableClock clock = new MutableClock();
    private TaskScheduler scheduler;
    private FakeDatabaseReadyListener playback;
    private FakeDatabaseReadyListener settings;
    private AtomicInteger migrations;
    private RuntimeException migrationFailure;
    private PlaybackDatabaseLifecycle lifecycle;
    private LogCapture log;

    @BeforeEach
    public void setUp() {
        scheduler = mock(TaskScheduler.class, invocation -> {
            scheduledTasks.add(invocation.getArgument(0));
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            futures.add(future);
            return future;
        });
        playback = new FakeDatabaseReadyListener("playback", calls);
        settings = new FakeDatabaseReadyListener("settings", calls);
        migrations = new AtomicInteger();
        lifecycle = new PlaybackDatabaseLifecycle(() -> {
            migrations.incrementAndGet();
            calls.add("migrate");
            if (migrationFailure != null) {
                throw migrationFailure;
            }
        }, List.of(playback, settings), scheduler, clock);
        log = new LogCapture(PlaybackDatabaseLifecycle.class);
    }

    @AfterEach
    public void tearDown() {
        log.close();
    }

    /** What the lifecycle logged, without the "Scheduled ..." lines ScheduledTaskManager adds on the same logger. */
    private List<String> logged(List<String> messages) {
        return messages.stream().filter(m -> !m.startsWith("Scheduled ")).toList();
    }

    /** The most recent run of the migration task: restarts replace it. */
    private void runMigrationTask() {
        scheduledTasks.get(scheduledTasks.size() - 1).run();
    }

    private static RuntimeException missingTable() {
        return new BadSqlGrammarException("insert", "insert into x", new SQLException("no such table", "42P01"));
    }

    @DisplayName("start schedules the migration to run at once and then every 30 seconds")
    @Test
    public void startSchedulesMigrationAtOnce() {
        lifecycle.start();

        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(TestClocks.INSTANT),
                eq(Duration.ofSeconds(30)));
        assertEquals(0, migrations.get());
    }

    @DisplayName("The migration runs first, then playback, then settings; then the task stops itself")
    @Test
    public void listenersRunInOrderAfterMigration() {
        lifecycle.start();

        runMigrationTask();

        assertEquals(List.of("migrate", "playback", "settings"), calls);
        assertEquals(1, futures.size());
        verify(futures.get(0)).cancel(false);
        assertEquals(List.of(), logged(log.warnings()));
    }

    @DisplayName("While the migration fails no listener runs, one warning is logged and the task keeps running; "
            + "on recovery the listeners run and one 'ready again' line is logged")
    @Test
    public void migrationFailureLogsOnStateChangeOnly() {
        migrationFailure = new IllegalStateException("Unable to obtain connection");
        lifecycle.start();

        runMigrationTask();
        runMigrationTask();
        runMigrationTask();

        assertEquals(List.of("migrate", "migrate", "migrate"), calls);
        verify(futures.get(0), never()).cancel(false);
        assertEquals(List.of("Database step 'migration' is not ready (IllegalStateException: Unable to obtain "
                + "connection); it is retried every 30 s"), logged(log.warnings()));

        migrationFailure = null;
        runMigrationTask();

        assertEquals(1, log.warnings().size());
        assertEquals("Database step 'migration' is ready again", logged(log.infos()).get(1));
        assertEquals(2, logged(log.infos()).size());
        assertEquals(List.of("migrate", "migrate", "migrate", "migrate", "playback", "settings"), calls);
        verify(futures.get(0)).cancel(false);
    }

    @DisplayName("The migration is not run again once it has succeeded, while a listener is still being retried")
    @Test
    public void migrationRunsOnceThenOnlyFailedListenersRetry() {
        settings.failure = new IllegalStateException("settings blip");
        lifecycle.start();

        runMigrationTask();
        runMigrationTask();

        assertEquals(List.of("migrate", "playback", "settings", "settings"), calls);
        assertEquals(1, migrations.get());
        assertEquals(1, playback.runs.get());
    }

    @DisplayName("A listener that throws does not stop the one after it, and the task keeps retrying it alone")
    @Test
    public void failingPlaybackDoesNotBlockSettings() {
        playback.failure = new IllegalStateException("seed failed");
        lifecycle.start();

        runMigrationTask();

        assertEquals(List.of("migrate", "playback", "settings"), calls);
        verify(futures.get(0), never()).cancel(false);
        assertEquals(List.of("Database step 'playback' is not ready (IllegalStateException: seed failed); it is "
                + "retried every 30 s"), logged(log.warnings()));

        playback.failure = null;
        runMigrationTask();

        assertEquals(List.of("migrate", "playback", "settings", "playback"), calls);
        assertEquals(1, settings.runs.get());
        verify(futures.get(0)).cancel(false);
        assertEquals("Database step 'playback' is ready again", logged(log.infos()).get(1));
        assertEquals(2, logged(log.infos()).size());
    }

    @DisplayName("A throwing settings listener does not undo playback, and playback is not run again")
    @Test
    public void failingSettingsDoesNotUndoPlayback() {
        settings.failure = new IllegalStateException("settings blip");
        lifecycle.start();

        runMigrationTask();
        settings.failure = null;
        runMigrationTask();

        assertEquals(List.of("migrate", "playback", "settings", "settings"), calls);
        assertEquals(1, playback.runs.get());
        verify(futures.get(0)).cancel(false);
    }

    @DisplayName("A listener failure is warned about once, however many times it is retried")
    @Test
    public void listenerFailureWarnsOnce() {
        settings.failure = new IllegalStateException("blip");
        lifecycle.start();

        runMigrationTask();
        runMigrationTask();
        runMigrationTask();

        assertEquals(1, logged(log.warnings()).size());
    }

    @DisplayName("Each listener is given the way to restart the migration")
    @Test
    public void listenersCanRestartTheMigration() {
        lifecycle.start();
        runMigrationTask();

        playback.restartMigration.run();

        assertNotNull(settings.restartMigration);
        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(TestClocks.INSTANT.plusSeconds(30)),
                eq(Duration.ofSeconds(30)));
    }

    @DisplayName("A restart waits the normal 30 seconds, never zero, and runs every step again")
    @Test
    public void restartWaitsThirtySecondsAndRerunsEverything() {
        lifecycle.start();
        runMigrationTask();

        settings.restartMigration.run();

        verify(scheduler, times(2)).scheduleAtFixedRate(any(Runnable.class), any(Instant.class),
                eq(Duration.ofSeconds(30)));
        verify(scheduler, never()).scheduleAtFixedRate(any(Runnable.class), eq(TestClocks.INSTANT.plusSeconds(1)),
                any(Duration.class));
        runMigrationTask();
        assertEquals(List.of("migrate", "playback", "settings", "migrate", "playback", "settings"), calls);
    }

    @DisplayName("A restart that arrives while a run is in progress is not cancelled by that run finishing")
    @Test
    public void restartDuringARunSurvivesTheRunEnding() {
        lifecycle.start();
        playback.duringRun = () -> {
            if (playback.runs.get() == 1) {
                playback.restartMigration.run();
            }
        };

        runMigrationTask();

        assertEquals(2, futures.size());
        verify(futures.get(1), never()).cancel(false);
        assertEquals(List.of("migrate", "playback"), calls);

        runMigrationTask();

        assertEquals(List.of("migrate", "playback", "migrate", "playback", "settings"), calls);
        verify(futures.get(1)).cancel(false);
    }

    @DisplayName("A restart that arrives during the migration discards that run's result, so every step runs again")
    @Test
    public void restartDuringMigrationDiscardsTheRun() {
        PlaybackDatabaseLifecycle restarting = new PlaybackDatabaseLifecycle(() -> {
            calls.add("migrate");
            settings.restartMigration.run();
        }, List.of(playback, settings), scheduler, clock);
        restarting.start();

        runMigrationTask();

        assertEquals(List.of("migrate"), calls);
        verify(futures.get(futures.size() - 1), never()).cancel(false);
    }

    @DisplayName("A table missing straight after a migration that succeeded stops everything, with one error")
    @Test
    public void missingTableAfterSuccessfulMigrationGivesUp() {
        playback.failure = missingTable();
        lifecycle.start();

        runMigrationTask();

        assertEquals(List.of("migrate", "playback"), calls);
        verify(futures.get(0)).cancel(false);
        assertEquals(1, log.errors().size());
        assertTrue(log.errors().get(0).startsWith("Database setup stopped: the playback table is missing "
                + "straight after a migration that succeeded."), log.errors().get(0));
        assertTrue(log.errors().get(0).contains("flyway repair"));

        playback.restartMigration.run();
        settings.restartMigration.run();
        runMigrationTask();

        assertEquals(1, futures.size());
        assertEquals(1, log.errors().size());
        assertEquals(List.of("migrate", "playback"), calls);
    }

    @DisplayName("A table that is missing from the settings listener also gives up, whichever listener it is")
    @Test
    public void missingTableFromSettingsGivesUp() {
        settings.failure = missingTable();
        lifecycle.start();

        runMigrationTask();

        assertEquals(List.of("migrate", "playback", "settings"), calls);
        assertEquals(1, log.errors().size());
        assertTrue(log.errors().get(0).contains("the settings table is missing"), log.errors().get(0));
    }

    @DisplayName("A second 42P01 within five minutes of a restart's migration succeeding gives up instead of looping")
    @Test
    public void secondRestartStraightAfterGivesUp() {
        lifecycle.start();
        runMigrationTask();
        playback.restartMigration.run();
        clock.advance(Duration.ofSeconds(30));
        runMigrationTask();
        int schedulesSoFar = futures.size();

        clock.advance(Duration.ofMillis(PlaybackDatabaseLifecycle.STRAIGHT_AFTER_MS - 1));
        playback.restartMigration.run();

        assertEquals(schedulesSoFar, futures.size());
        assertEquals(1, log.errors().size());
        assertTrue(log.errors().get(0).startsWith("Database setup stopped: a table is missing again straight after "
                + "a migration that succeeded."), log.errors().get(0));
        playback.restartMigration.run();
        assertEquals(1, log.errors().size());
    }

    @DisplayName("A restart exactly five minutes after the last restart's migration is a new incident, and allowed")
    @Test
    public void restartAfterTheWindowIsAllowed() {
        lifecycle.start();
        runMigrationTask();
        playback.restartMigration.run();
        runMigrationTask();
        int schedulesSoFar = futures.size();

        clock.advance(Duration.ofMillis(PlaybackDatabaseLifecycle.STRAIGHT_AFTER_MS));
        playback.restartMigration.run();

        assertEquals(schedulesSoFar + 1, futures.size());
        assertEquals(List.of(), log.errors());
    }

    @DisplayName("A task run that starts after the lifecycle gave up stops its own schedule")
    @Test
    public void runAfterGivingUpStops() {
        playback.failure = missingTable();
        lifecycle.start();
        runMigrationTask();
        playback.failure = null;

        runMigrationTask();

        assertEquals(List.of("migrate", "playback"), calls);
        assertEquals(1, log.errors().size());
    }

    @DisplayName("stop cancels a migration that is still being retried and stops every listener")
    @Test
    public void stopCancelsMigrationAndListeners() {
        lifecycle.start();

        lifecycle.stop();

        verify(futures.get(0)).cancel(false);
        assertEquals(1, playback.stops.get());
        assertEquals(1, settings.stops.get());
    }

    @DisplayName("Nothing touches the database until start is called")
    @Test
    public void nothingBeforeStart() {
        assertEquals(0, migrations.get());
        assertEquals(List.of(), calls);
        assertEquals(List.of(), scheduledTasks);
    }
}
