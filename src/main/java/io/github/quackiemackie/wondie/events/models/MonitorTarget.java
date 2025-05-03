package io.github.quackiemackie.wondie.events.models;

/**
 * Represents a target to be monitored, including its address, monitoring type, and online status.
 */
public class MonitorTarget {
    /**
     * The address people use to connect to the service.
     */
    private final String publicAddress;

    /**
     * The address of the target to monitor (IP/hostname/domain).
     */
    private final String address;

    /**
     * The type of monitoring task to perform.
     */
    private final MonitorType type;

    /**
     * Whether the target is currently online or reachable.
     */
    private boolean isOnline;

    public MonitorTarget(String publicAddress, String address, MonitorType type) {
        this.publicAddress = publicAddress;
        this.address = address;
        this.type = type;
    }

    public String getPublicAddress() {
        return publicAddress;
    }

    public String getAddress() {
        return address;
    }

    public MonitorType getType() {
        return type;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        this.isOnline = online;
    }
}
