package gregochr;

import gregochr.commands.*;
import gregochr.services.plex.beans.PlexMetadataBean;
import gregochr.services.plex.beans.PlexRootBean;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PlexCommandFactoryTest {
    @DisplayName("Test Create Plex Play Command")
    @Test
    public void testCreatePlexPlayCommand() {
        PlexRootBean plexRootBean = getPlexRootBean("media.play");

        PlexCommandFactory factory = new PlexCommandFactory(plexRootBean);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexPlayCommand);
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
    }

    @DisplayName("Test Create Plex Resume Command")
    @Test
    public void testCreatePlexResumeCommand() {
        PlexRootBean plexRootBean = getPlexRootBean("media.resume");

        PlexCommandFactory factory = new PlexCommandFactory(plexRootBean);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexResumeCommand);
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
    }

    @DisplayName("Test Create Plex Stop Command")
    @Test
    public void testCreatePlexStopCommand() {
        PlexRootBean plexRootBean = getPlexRootBean("media.stop");

        PlexCommandFactory factory = new PlexCommandFactory(plexRootBean);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexStopCommand);
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
    }

    @DisplayName("Test Create Plex Pause Command")
    @Test
    public void testCreatePlexPauseCommand() {
        PlexRootBean plexRootBean = getPlexRootBean("media.pause");

        PlexCommandFactory factory = new PlexCommandFactory(plexRootBean);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexPauseCommand);
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
    }

    @DisplayName("Test Create Plex Scrobble Command")
    @Test
    public void testCreatePlexScrobbleCommand() {
        PlexRootBean plexRootBean = getPlexRootBean("media.scrobble");

        PlexCommandFactory factory = new PlexCommandFactory(plexRootBean);
        PlexCommand plexCommand = factory.getPlexCommand();

        assert(plexCommand != null);
        assert(plexCommand instanceof PlexScrobbleCommand);
        assert(plexCommand.getTitle().equals("Good Will Hunting"));
        assert(plexCommand.getDuration() == 123);
    }

    @Nonnull
    private static PlexRootBean getPlexRootBean(String event) {
        PlexRootBean plexRootBean = new PlexRootBean();
        plexRootBean.setEvent(event);

        PlexMetadataBean plexMetadataBean = new PlexMetadataBean();
        plexMetadataBean.setTitle("Good Will Hunting");
        plexMetadataBean.setDuration(123);
        plexRootBean.setMetadata(plexMetadataBean);
        return plexRootBean;
    }
}
