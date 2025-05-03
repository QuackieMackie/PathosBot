package io.github.quackiemackie.wondie.metrics;

import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import oshi.SystemInfo;
import oshi.hardware.NetworkIF;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.Instant;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MetricsService {
    private static final Gauge guildCount = Gauge.build()
            .name("discord_guild_count")
            .help("Number of Discord guilds connected")
            .register();

    private static final Gauge userCount = Gauge.build()
            .name("discord_user_count")
            .help("Number of users supported across all servers")
            .register();

    private static final Gauge uptime = Gauge.build()
            .name("bot_uptime_seconds")
            .help("Bot uptime in seconds")
            .register();

    private static final Gauge workingSet = Gauge.build()
            .name("process_working_set_bytes")
            .help("Physical memory used by process")
            .register();

    private static final Gauge privateMemory = Gauge.build()
            .name("process_private_memory_bytes")
            .help("Private memory used by process")
            .register();

    private static final Gauge cpuUsage = Gauge.build()
            .name("process_cpu_percent")
            .help("Approximate CPU usage percentage")
            .register();

    private static final Gauge networkBytesSent = Gauge.build()
            .name("network_bytes_sent_total")
            .help("Total bytes sent")
            .register();

    private static final Gauge networkBytesReceived = Gauge.build()
            .name("network_bytes_received_total")
            .help("Total bytes received")
            .register();

    private final Instant startTime;
    private final OperatingSystemMXBean osBean;
    private final HTTPServer server;
    private final Timer timer;

    public MetricsService() throws IOException {
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        DefaultExports.initialize();
        this.startTime = Instant.now();
        this.server = new HTTPServer(5000);
        this.timer = new Timer();
        startCollectionLoop();
    }

    private void startCollectionLoop() {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateUptime();
                updateMemory();
                updateCpu();
                updateNetwork();
            }
        }, 0, 15000);
    }

    private void updateUptime() {
        var seconds = Instant.now().getEpochSecond() - startTime.getEpochSecond();
        uptime.set(seconds);
    }

    private void updateMemory() {
        var runtime = Runtime.getRuntime();
        workingSet.set(runtime.totalMemory() - runtime.freeMemory());
        privateMemory.set(runtime.totalMemory());
    }

    private void updateCpu() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean platformBean) {
            double cpuLoad = platformBean.getProcessCpuLoad() * 100;
            cpuUsage.set(cpuLoad);
        }
    }

    private void updateNetwork() {
        long sent = 0;
        long received = 0;

        SystemInfo systemInfo = new SystemInfo();
        List<NetworkIF> networkInterfaces = systemInfo.getHardware().getNetworkIFs();

        for (NetworkIF ni : networkInterfaces) {
            if (ni.getBytesSent() == 0 && ni.getBytesRecv() == 0) {
                continue;
            }

            sent += ni.getBytesSent();
            received += ni.getBytesRecv();
        }

        networkBytesSent.set(sent);
        networkBytesReceived.set(received);
    }


    public static void setGuildCount(int count) {
        guildCount.set(count);
    }

    public static void setUserCount(int count) {
        userCount.set(count);
    }

    public void stop() {
        timer.cancel();
        server.close();
    }
}
