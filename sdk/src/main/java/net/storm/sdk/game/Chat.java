package net.storm.sdk.game;

import net.runelite.api.ChatLineBuffer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Game chat helpers.
 */
public final class Chat {

    private static final Logger log = LoggerFactory.getLogger(Chat.class);

    private Chat() {
    }

    /**
     * Adds a local game message via {@link Client#addChatMessage}.
     *
     * @return true if the client accepted the message
     */
    public static boolean sendGameMessage(String message) {
        if (message == null) {
            return false;
        }
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                c.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
                return true;
            } catch (Throwable t) {
                log.warn("[Chat] sendGameMessage failed: {}", t.toString());
                return false;
            }
        }, false));
    }

    /**
     * Recent chat lines from the game-message buffer (best-effort).
     *
     * @stub Full Storm chat history API not mirrored; returns up to {@code limit} lines.
     */
    public static List<String> getRecentMessages(int limit) {
        int max = Math.max(0, limit);
        return Static.callOnClientThread(() -> {
            List<String> out = new ArrayList<>();
            Client c = Static.getClient();
            if (c == null || max == 0) {
                return out;
            }
            try {
                Map<Integer, ChatLineBuffer> map = c.getChatLineMap();
                if (map == null) {
                    return out;
                }
                addFromBuffer(out, map.get(ChatMessageType.GAMEMESSAGE.getType()), max);
                if (out.isEmpty()) {
                    addFromBuffer(out, map.get(ChatMessageType.CONSOLE.getType()), max);
                }
            } catch (Throwable t) {
                log.debug("[Chat] getRecentMessages stub/fail: {}", t.toString());
            }
            return out;
        }, new ArrayList<>());
    }

    /**
     * Clan / friends / guest / public chat — star-calls staan hier, niet in game-messages.
     */
    public static List<String> getRecentPlayerChat(int limit) {
        int max = Math.max(0, limit);
        return Static.callOnClientThread(() -> {
            List<String> out = new ArrayList<>();
            Client c = Static.getClient();
            if (c == null || max == 0) {
                return out;
            }
            try {
                Map<Integer, ChatLineBuffer> map = c.getChatLineMap();
                if (map == null) {
                    return out;
                }
                ChatMessageType[] types = {
                        ChatMessageType.FRIENDSCHAT,
                        ChatMessageType.CLAN_CHAT,
                        ChatMessageType.CLAN_GUEST_CHAT,
                        ChatMessageType.CLAN_GIM_CHAT,
                        ChatMessageType.PUBLICCHAT
                };
                for (ChatMessageType t : types) {
                    if (t == null) {
                        continue;
                    }
                    addFromBuffer(out, map.get(t.getType()), max);
                }
            } catch (Throwable t) {
                log.debug("[Chat] getRecentPlayerChat: {}", t.toString());
            }
            return out;
        }, new ArrayList<>());
    }

    private static void addFromBuffer(List<String> out, ChatLineBuffer buf, int max) {
        if (buf == null || out.size() >= max) {
            return;
        }
        MessageNode[] lines;
        try {
            lines = buf.getLines();
        } catch (Throwable t) {
            return;
        }
        if (lines == null) {
            return;
        }
        int start = Math.max(0, lines.length - max);
        for (int i = start; i < lines.length; i++) {
            if (out.size() >= max * 4) {
                return;
            }
            MessageNode n = lines[i];
            if (n == null) {
                continue;
            }
            String v = n.getValue();
            if (v != null && !v.isBlank()) {
                out.add(v);
            }
        }
    }
}
