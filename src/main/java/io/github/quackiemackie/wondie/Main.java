package io.github.quackiemackie.wondie;

import io.github.quackiemackie.wondie.events.EventHandler;
import io.github.quackiemackie.wondie.metrics.MetricsService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final String BOT_TOKEN_PATH = "/run/secrets/bot_token";

    public static void main(String[] args) {
        MetricsService metricsService = startMetricsService();

        String botToken = readSecret();

        EventHandler eventHandler = new EventHandler();
        JDA api = initializeJDA(botToken, eventHandler);

        updateGuildMetrics(api);

        addShutdownHook(metricsService, api, eventHandler);
    }

    private static MetricsService startMetricsService() {
        try {
            MetricsService metricsService = new MetricsService();
            logger.info("Metrics service successfully started and exposed on port 5000.");
            return metricsService;
        } catch (IOException e) {
            logger.error("Failed to start metrics service: {}", e.getMessage(), e);
            System.exit(1);
            return null;
        }
    }

    private static JDA initializeJDA(String botToken, EventHandler eventHandler) {
        try {
            JDA api = JDABuilder.createDefault(botToken)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS, GatewayIntent.DIRECT_MESSAGES)
                    .addEventListeners(eventHandler)
                    .setEnableShutdownHook(false)
                    .build();

            api.awaitReady();
            logger.info("{} successfully started.", api.getSelfUser().getName());
            return api;
        } catch (InterruptedException e) {
            logger.error("Error waiting for JDA to initialize: {}", e.getMessage(), e);
            System.exit(1);
            return null;
        }
    }

    private static void updateGuildMetrics(JDA api) {
        List<Guild> guilds = api.getGuilds();
        String guildList = guilds.stream()
                .map(guild -> String.format("%s [%s]", guild.getName(), guild.getId()))
                .collect(Collectors.joining(", "));
        logger.info("{} is connected to the following guilds: {}", api.getSelfUser().getName(), guildList);

        int guildCount = guilds.size();
        int userCount = guilds.stream().mapToInt(Guild::getMemberCount).sum();

        MetricsService.setGuildCount(guildCount);
        MetricsService.setUserCount(userCount);

        logger.info("Metrics updated: Guilds = {}, Users = {}", guildCount, userCount);
    }

    private static void addShutdownHook(MetricsService metricsService, JDA api, EventHandler eventHandler) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Starting shutdown sequence...");

            try {
                eventHandler.onShutdown(api);
            } catch (Exception e) {
                logger.error("Error during shutdown sequence: {}", e.getMessage(), e);
            }

            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {}

            logger.info("Shutting down JDA...");
            api.shutdown();

            logger.info("Shutting down metrics service...");
            metricsService.stop();

            logger.info("Shutdown sequence complete.");
            api.shutdown();
        }));
    }

    private static String readSecret() {
        try {
            return Files.readString(Path.of(Main.BOT_TOKEN_PATH)).trim();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read secret from: " + BOT_TOKEN_PATH, e);
        }
    }
}