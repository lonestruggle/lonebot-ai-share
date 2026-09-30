package net.storm.api.account;

/**
 * Storm-compat game account (Jagex OAuth / legacy).
 */
public final class GameAccount {

    private final String username;
    private final String password;
    private final String displayName;
    private final String sessionId;
    private final String characterId;

    public GameAccount(String username, String password) {
        this(username, password, username, password, "");
    }

    public GameAccount(String username, String password, String displayName, String sessionId, String characterId) {
        this.username = username != null ? username : "";
        this.password = password != null ? password : "";
        this.displayName = displayName != null ? displayName : this.username;
        this.sessionId = sessionId != null ? sessionId : "";
        this.characterId = characterId != null ? characterId : "";
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getCharacterId() {
        return characterId;
    }
}
