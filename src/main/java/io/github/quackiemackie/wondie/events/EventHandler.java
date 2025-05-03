package io.github.quackiemackie.wondie.events;

import io.github.quackiemackie.wondie.events.models.ReactionRolePair;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventHandler extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(EventHandler.class);
    List<String> greetings = Arrays.asList("hello", "hi", "hey", "greetings", "good morning", "good evening");
    private static final Map<Long, ReactionRolePair> trackedMessages = new HashMap<>();

    static {
        trackedMessages.put(1365748781055737926L, new ReactionRolePair("👍", "Peeps"));
    }
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String message = event.getMessage().getContentRaw();

        if (greetings.contains(message.toLowerCase())) {
            event.getChannel().addReactionById(event.getMessage().getId(), Emoji.fromUnicode("U+1F44B")).queue();
        }
    }

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        if (!trackedMessages.containsKey(event.getMessageIdLong())) return;

        ReactionRolePair rolePair = trackedMessages.get(event.getMessageIdLong());
        String expectedEmote = rolePair.getEmote();
        String roleName = rolePair.getRoleName();

        String reactionEmote = event.getReaction().getEmoji().getName();
        if (!reactionEmote.equals(expectedEmote)) return;

        Member member = event.getMember();
        if (member == null) return;

        Guild guild = member.getGuild();
        Role role = guild.getRolesByName(roleName, true).stream().findFirst().orElse(null);

        if (role == null) {
            logger.warn("Role '{}' not found in guild '{}'.", roleName, guild.getName());
            return;
        }

        if (!member.getRoles().contains(role)) {
            guild.addRoleToMember(member, role).queue(
                    success -> logger.info("Assigned role '{}' to {} for reacting to message {}.",
                            roleName, member.getEffectiveName(), event.getMessageIdLong()),
                    error -> logger.error("Failed to assign role '{}' to {}: {}",
                            roleName, member.getEffectiveName(), error.getMessage())
            );
        }
    }

    @Override
    public void onMessageReactionRemove(@NotNull MessageReactionRemoveEvent event) {
        if (!trackedMessages.containsKey(event.getMessageIdLong())) return;

        ReactionRolePair rolePair = trackedMessages.get(event.getMessageIdLong());
        String expectedEmote = rolePair.getEmote();
        String roleName = rolePair.getRoleName();

        String reactionEmote = event.getReaction().getEmoji().getName();
        if (!reactionEmote.equals(expectedEmote)) return;

        Member member;
        try {
            member = event.retrieveMember().complete();
        } catch (Exception e) {
            logger.warn("Failed to retrieve member for reaction removal: {}", e.getMessage());
            return;
        }
        Guild guild = event.getGuild();
        Role role = guild.getRolesByName(roleName, true).stream().findFirst().orElse(null);

        if (role == null) {
            logger.warn("Role '{}' not found in guild '{}'.", roleName, guild.getName());
            return;
        }

        if (member.getRoles().contains(role)) {
            guild.removeRoleFromMember(member, role).queue(
                    success -> logger.info("Removed role '{}' from {} for removing reaction on message {}.",
                            roleName, member.getEffectiveName(), event.getMessageIdLong()),
                    error -> logger.error("Failed to remove role '{}' from {}: {}",
                            roleName, member.getEffectiveName(), error.getMessage())
            );
        }
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        StatusMonitor.startMonitoring(event.getJDA(), logger);
    }

    public void onShutdown(JDA jda) {
        StatusMonitor.stopMonitoring(jda, logger);
    }
}
