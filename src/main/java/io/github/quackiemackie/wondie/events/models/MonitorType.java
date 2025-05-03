package io.github.quackiemackie.wondie.events.models;

/**
 * Represents different types of monitoring tasks that can be performed.
 */
public enum MonitorType {
    /**
     * Check if a host is reachable via ICMP.
     */
    PING,

    /**
     * Check if a website or API responds successfully to HTTP requests.
     */
    HTTP,

    /**
     * Check if a specific port on a host is open and accepting connections.
     */
    TCP,

    /**
     * Check if the DNS resolution works for a host.
     */
    DNS,

    /**
     * Check if a specific process is running on the local machine.
     */
    PROCESS
}
