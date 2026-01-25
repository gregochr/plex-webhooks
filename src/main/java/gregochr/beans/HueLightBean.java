package gregochr.beans;

public class HueLightBean {
    private String key;
    private String name;
    private boolean on;
    private boolean reachable;

    public HueLightBean(String key, String name, boolean on, boolean reachable) {
        this.key = key;
        this.name = name;
        this.on = on;
        this.reachable = reachable;
    }

    public String getKey() { return this.key; };

    public String getName() {
        return this.name;
    }

    public String isOn() { return this.on ? "On" : "Off"; }

    public String isReachable() { return this.reachable ? "Yes" : "No"; }

    public String toString() {
        return new String("Key:" + getKey() + ", Name: " + this.getName() + ", State: " + this.isOn() + ", Reachable: " + this.isReachable() + "\n");
    }
}
