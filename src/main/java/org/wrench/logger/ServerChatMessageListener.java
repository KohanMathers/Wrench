package org.wrench.logger;

import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerChatEvent;

public final class ServerChatMessageListener {

    public static void register(GlobalEventHandler handler) {
        handler.addListener(PlayerChatEvent.class, event -> {
            String message = event.getRawMessage();
            String playerName = event.getPlayer().getUsername();
            org.wrench.Main.log("info", "<" + playerName + "> " + message);
        });
    }
}
