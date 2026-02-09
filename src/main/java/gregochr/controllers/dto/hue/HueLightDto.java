package gregochr.controllers.dto.hue;

public class HueLightDto {
    private String key;
    private String name;
    private boolean on;
    private boolean reachable;

    public HueLightDto(String key, String name, boolean on, boolean reachable) {
        this.key = key;
        this.name = name;
        this.on = on;
        this.reachable = reachable;
    }

    public String getKey() { return this.key; };

    public String getName() {
        return this.name;
    }

    public boolean isOn() { return this.on; }

    public boolean isReachable() { return this.reachable; }

    @Override
    public String toString() {
        return "Key:" + getKey() + ", Name: " + getName() + ", State: " + (on ? "On" : "Off") + ", Reachable: " + (reachable ? "Yes" : "No") + "\n";
    }
}
