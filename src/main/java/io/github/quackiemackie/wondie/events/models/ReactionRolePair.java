package io.github.quackiemackie.wondie.events.models;

public class ReactionRolePair {
    private final String emote;
    private final String roleName;

    public ReactionRolePair(String emote, String roleName) {
        this.emote = emote;
        this.roleName = roleName;
    }

    public String getEmote() {
        return emote;
    }

    public String getRoleName() {
        return roleName;
    }
}
