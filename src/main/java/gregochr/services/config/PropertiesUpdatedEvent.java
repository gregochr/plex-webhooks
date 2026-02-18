package gregochr.services.config;

import org.springframework.context.ApplicationEvent;

import java.util.Map;

public class PropertiesUpdatedEvent extends ApplicationEvent {
    private final Map<String, String> updatedProperties;

    public PropertiesUpdatedEvent(Object source, Map<String, String> updatedProperties) {
        super(source);
        this.updatedProperties = updatedProperties;
    }

    public Map<String, String> getUpdatedProperties() {
        return updatedProperties;
    }
}
