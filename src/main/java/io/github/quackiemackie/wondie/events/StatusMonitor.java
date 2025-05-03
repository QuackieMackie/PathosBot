package io.github.quackiemackie.wondie.events;

import io.github.quackiemackie.wondie.events.models.MonitorTarget;
import io.github.quackiemackie.wondie.events.models.MonitorType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;

import java.awt.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class StatusMonitor {

    private static final Map<String, MonitorTarget> targets = new LinkedHashMap<>();
    private static final Map<Long, Message> statusMessages = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final long UPDATE_INTERVAL = TimeUnit.MINUTES.toSeconds(1);

    private static volatile boolean isMonitoring = false;

    static {
        targets.put("Sylphian Proxy", new MonitorTarget("mc.sylphian.net", "sylphian-proxy:25565", MonitorType.TCP));
        targets.put("Sylphian Hub", new MonitorTarget("mc.sylphian.net", "sylphian-hub:25565", MonitorType.TCP));
        targets.put("Sylphian Survival", new MonitorTarget("mc.sylphian.net", "sylphian-survival:25565", MonitorType.TCP));
    }

    /**
     * Starts the monitoring loop and posts an embed message to all "server-status" channels.
     */
    public static void startMonitoring(JDA jda, Logger logger) {
        if (isMonitoring) {
            logger.warn("Monitoring is already running!");
            return;
        }

        logger.info("Starting the StatusMonitor...");
        isMonitoring = true;

        scheduler.scheduleAtFixedRate(() -> {
            try {
                ensureStatusChannels(jda, logger);
                updateStatus(logger);
                logger.info("Status check and update cycle completed.");
            } catch (Exception e) {
                logger.error("Error during the monitoring process: {}", e.getMessage());
            }
        }, 0, UPDATE_INTERVAL, TimeUnit.SECONDS);
    }

    /**
     * Stops the monitoring loop and cancels tasks.
     */
    public static void stopMonitoring(JDA jda, Logger logger) {
        if (!isMonitoring) {
            logger.warn("Monitoring is not currently running.");
            return;
        }

        logger.info("Stopping the StatusMonitor...");
        isMonitoring = false;

        scheduler.shutdownNow();

        try {
            setBotStatusToOffline(jda, logger);
        } catch (Exception e) {
            logger.error("Error while setting bot status to offline during shutdown.", e);
        }
    }

    /**
     * Ensures all "server-status" channels across guilds have a corresponding status message.
     */
    private static void ensureStatusChannels(JDA jda, Logger logger) {
        for (Guild guild : jda.getGuilds()) {
            TextChannel channel = guild.getTextChannels()
                    .stream()
                    .filter(c -> c.getName().equalsIgnoreCase("server-status"))
                    .findFirst()
                    .orElse(null);

            if (channel != null) {
                if (!statusMessages.containsKey(guild.getIdLong())) {
                    List<Message> messages = channel.getHistory().retrievePast(1).complete();
                    Message statusMessage = messages.isEmpty()
                            ? channel.sendMessageEmbeds(createStatusEmbed()).complete()
                            : messages.getFirst();

                    statusMessages.put(guild.getIdLong(), statusMessage);
                    logger.info("Created status message for guild '{}'", guild.getName());
                }
            } else {
                if (statusMessages.remove(guild.getIdLong()) != null) {
                    logger.warn("Removed 'server-status' message for guild '{}' as the channel no longer exists.", guild.getName());
                }
            }
        }
    }

    /**
     * Updates the status messages across all guilds.
     */
    private static void updateStatus(Logger logger) {
        updateTargetStatuses(logger);

        for (Map.Entry<Long, Message> entry : statusMessages.entrySet()) {
            long guildId = entry.getKey();
            Message message = entry.getValue();

            try {
                message.editMessageEmbeds(createStatusEmbed()).queue();
                logger.info("Updated status message for guild ID: {}", guildId);
            } catch (Exception e) {
                logger.error("Failed to update status message for guild ID: {}", guildId, e);
            }
        }
    }

    /**
     * Checks the status of all monitor targets and updates their online status.
     */
    private static void updateTargetStatuses(Logger logger) {
        for (MonitorTarget target : targets.values()) {
            try {
                target.setOnline(checkTarget(target));
            } catch (Exception e) {
                logger.error("Error while checking target '{}': {}", target.getPublicAddress(), e.getMessage());
                target.setOnline(false);
            }
        }
    }

    /**
     * Performs a status check on the target.
     */
    private static boolean checkTarget(MonitorTarget target) throws IOException {
        switch (target.getType()) {
            case PING:
                return InetAddress.getByName(target.getAddress()).isReachable(1000);

            case HTTP:
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(target.getAddress().startsWith("http") ?
                                java.net.URI.create(target.getAddress()) :
                                java.net.URI.create("https://" + target.getAddress()))
                        .build();
                try {
                    HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                    return response.statusCode() == 200;
                } catch (InterruptedException | IOException e) {
                    return false;
                }

            case TCP:
                String[] parts = target.getAddress().split(":");
                if (parts.length != 2) return false;

                String host = parts[0];
                int port = Integer.parseInt(parts[1]);

                try (Socket ignored = new Socket(host, port)) {
                    return true;
                } catch (Exception e) {
                    return false;
                }

            case DNS:
                return InetAddress.getAllByName(target.getAddress()).length > 0;

            case PROCESS:
                //TODO: Implement process monitoring
                return false;

            default:
                return false;
        }
    }

    /**
     * Creates an embed object representing the current status of all targets.
     */
    private static MessageEmbed createStatusEmbed() {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Status Dashboard")
                .setDescription("Provides real-time status of services.\nThis embed message will update every minute.")
                .setColor(Color.GREEN);

        long nowSeconds = System.currentTimeMillis() / 1000;

        embedBuilder.addField("🟢 **Am I Online?**",
                String.format("Yes, I'm **online**! (as of <t:%d:R>)", nowSeconds - 30), false);

        for (Map.Entry<String, MonitorTarget> entry : targets.entrySet()) {
            String name = entry.getKey();
            MonitorTarget target = entry.getValue();
            String status = target.isOnline() ? "🟢 Online" : "🔴 Offline";

            embedBuilder.addField(name,
                    String.format("%s\nConnect: `%s`", status, target.getPublicAddress()), false);
        }

        return embedBuilder.build();
    }

    /**
     * Sets all status channels to indicate that the bot is offline.
     */
    private static void setBotStatusToOffline(JDA jda, Logger logger) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle("Status Dashboard")
                .setDescription("Provides real-time status of services.\nThis embed message will update every minute.")
                .setColor(Color.RED)
                .addField("🔴 **Am I Online?**",
                        String.format("No! I'm **offline**! (as of <t:%d:R>)", nowSeconds - 30), false);

        jda.getGuilds().forEach(guild -> {
            TextChannel channel = guild.getTextChannels()
                    .stream()
                    .filter(c -> c.getName().equalsIgnoreCase("server-status"))
                    .findFirst()
                    .orElse(null);

            if (channel != null) {
                try {
                    List<Message> messages = channel.getHistory().retrievePast(1).complete();
                    Message msg = messages.isEmpty()
                            ? channel.sendMessageEmbeds(embedBuilder.build()).complete()
                            : messages.getFirst();

                    msg.editMessageEmbeds(embedBuilder.build()).complete();
                } catch (Exception e) {
                    logger.error("Error updating offline status for guild '{}': {}", guild.getName(), e.getMessage());
                }
            }
        });
    }
}
