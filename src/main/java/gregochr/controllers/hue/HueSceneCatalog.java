package gregochr.controllers.hue;

import gregochr.hue.client.HueBridgeClient;
import gregochr.services.ScheduledTaskManager;
import gregochr.services.config.ConfigService;
import gregochr.services.config.PropertiesUpdatedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scene names, rooms and levels, cached from the Hue bridge's resources document.
 *
 * <p>The bridge call fetches everything on the bridge, so it runs on its own slow schedule
 * ({@code hue.scene-catalog-interval}, default 5 minutes), once shortly after boot, and after a start or stop scene
 * change. No request to CineLight ever triggers it: a public request for an unknown scene id gets {@code null}, not a
 * bridge call. When the bridge is unreachable or answers something unusable the last good catalog is kept (empty
 * before the first success). Nothing is fetched while Hue integration is switched off.
 *
 * <p>The resources document is read as a Jackson tree because {@code hue-java-client}'s DTOs do not carry a scene's
 * {@code group} or {@code actions}, and that library stays unchanged.
 */
@Component
public class HueSceneCatalog {
    private static final Logger LOGGER = LogManager.getLogger(HueSceneCatalog.class);
    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private final HueBridgeClient hueBridgeClient;
    private final ConfigService configService;
    private final HueSceneController hueSceneController;
    private final TaskScheduler taskScheduler;
    private final Clock clock;
    private final ScheduledTaskManager scheduledTaskManager;
    private volatile List<HueSceneSummary> scenes = List.of();

    /** A scene id and its catalog entry, which is null when the id is not in the catalog. */
    public record Selection(String id, HueSceneSummary summary) {
    }

    /** What {@code GET /api/hue/scenes} returns. */
    public record View(List<HueSceneSummary> scenes, Selection start, Selection stop) {
    }

    public HueSceneCatalog(
            HueBridgeClient hueBridgeClient,
            ConfigService configService,
            HueSceneController hueSceneController,
            @Value("${hue.scene-catalog-interval:300000}") long intervalMs,
            TaskScheduler taskScheduler,
            Clock clock) {
        this.hueBridgeClient = hueBridgeClient;
        this.configService = configService;
        this.hueSceneController = hueSceneController;
        this.taskScheduler = taskScheduler;
        this.clock = clock;
        this.scheduledTaskManager = new ScheduledTaskManager(taskScheduler, clock, intervalMs,
                "hue.scene-catalog-interval", "hue scene catalog refresh", this::refresh, LOGGER);
    }

    @PostConstruct
    public void init() {
        scheduledTaskManager.start();
        refreshSoon();
    }

    @PreDestroy
    public void destroy() {
        scheduledTaskManager.shutdown();
    }

    @EventListener
    public void onPropertiesUpdated(PropertiesUpdatedEvent event) {
        scheduledTaskManager.onPropertiesUpdated(event);
        Map<String, String> updates = event.getUpdatedProperties();
        if (updates.containsKey("hue.start-scene") || updates.containsKey("hue.stop-scene")) {
            refreshSoon();
        }
    }

    /** The cached scenes plus the configured start and stop scenes. Never calls the bridge. */
    public View view() {
        List<HueSceneSummary> current = scenes;
        return new View(current, select(current, hueSceneController.getStartScene()),
                select(current, hueSceneController.getStopScene()));
    }

    /**
     * Re-reads the catalog from the bridge. Runs on the scheduler, so it must never throw: an exception would
     * cancel every later run of the fixed-rate task.
     */
    public void refresh() {
        if (!configService.isHueIntegrationEnabled()) {
            LOGGER.debug("Hue integration disabled, not refreshing the scene catalog");
            return;
        }
        try {
            String body = hueBridgeClient.getResources();
            if (body == null) {
                LOGGER.warn("Hue bridge unreachable, keeping the last scene catalog ({} scenes)", scenes.size());
                return;
            }
            scenes = List.copyOf(parse(body));
            LOGGER.info("Scene catalog refreshed: {} scenes", scenes.size());
        } catch (RuntimeException e) {
            LOGGER.warn("Could not refresh the scene catalog, keeping the last one ({} scenes): {}",
                    scenes.size(), e.getMessage());
        }
    }

    private void refreshSoon() {
        taskScheduler.schedule(this::refresh, clock.instant());
    }

    private static Selection select(List<HueSceneSummary> catalog, String sceneId) {
        HueSceneSummary summary = catalog.stream()
                .filter(scene -> scene.id().equals(sceneId))
                .findFirst()
                .orElse(null);
        return new Selection(sceneId, summary);
    }

    private static List<HueSceneSummary> parse(String body) {
        JsonNode data = OBJECT_MAPPER.readTree(body).path("data");
        if (!data.isArray()) {
            throw new IllegalArgumentException("the bridge response has no data array");
        }
        Map<String, String> groupNames = new HashMap<>();
        for (JsonNode resource : data) {
            String type = resource.path("type").asString("");
            if (type.equals("room") || type.equals("zone")) {
                groupNames.put(resource.path("id").asString(""), resource.path("metadata").path("name").asString(null));
            }
        }
        List<HueSceneSummary> summaries = new ArrayList<>();
        for (JsonNode resource : data) {
            if (resource.path("type").asString("").equals("scene") && resource.path("id").isString()) {
                String groupId = resource.path("group").path("rid").asString(null);
                summaries.add(new HueSceneSummary(
                        resource.path("id").asString(),
                        resource.path("metadata").path("name").asString(null),
                        groupId,
                        groupNames.get(groupId),
                        resource.path("group").path("rtype").asString(null),
                        level(resource.path("actions"))));
            }
        }
        return summaries;
    }

    /**
     * The mean brightness over the scene's actions, rounded; an action that switches its light off counts as 0.
     * Null when no action says anything about brightness or on/off, so a number is never invented.
     */
    private static Integer level(JsonNode actions) {
        double total = 0;
        int counted = 0;
        for (JsonNode actionEntry : actions) {
            JsonNode action = actionEntry.path("action");
            JsonNode on = action.path("on").path("on");
            JsonNode brightness = action.path("dimming").path("brightness");
            if (on.isBoolean() && !on.asBoolean()) {
                counted++;
            } else if (brightness.isNumber()) {
                total += brightness.asDouble();
                counted++;
            }
        }
        return counted == 0 ? null : Math.toIntExact(Math.round(total / counted));
    }
}
