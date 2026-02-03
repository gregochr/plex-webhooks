package gregochr.controllers.hue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HueController {
    @Value("${hue.hub-address}")
    protected String hubAddress;

    @Value("${hue.hub-id}")
    protected String hubId;

    @Value("${hue.login}")
    protected String login;

    @Value("${hue.key}")
    protected String key;
}
