package gregochr.controllers.dto.hue;

public record HueLightDto(String key, String name, boolean on, boolean reachable) {

    @Override
    public String toString() {
        return "Key:%s, Name: %s, State: %s, Reachable: %s%n".formatted(
                key, name, on ? "On" : "Off", reachable ? "Yes" : "No");
    }
}
