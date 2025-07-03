package org.wrench.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerDeathEvent;

public final class ServerChatMessageListener {

    public static void register(GlobalEventHandler handler) {
        handler.addListener(PlayerChatEvent.class, event -> {
            String message = event.getRawMessage();
            String playerName = event.getPlayer().getUsername();
            org.wrench.Main.log("info", "<" + playerName + "> " + message);
        });

        handler.addListener(PlayerDeathEvent.class, event -> {
            String playerName = event.getPlayer().getUsername();
            Component deathMessage = event.getDeathText();
            String deathMessageText = deathMessage != null ? LegacyComponentSerializer.legacySection().serialize(deathMessage) : "died";
            if (!deathMessageText.isEmpty()) {
                deathMessageText = Character.toLowerCase(deathMessageText.charAt(0)) + deathMessageText.substring(1);
            }
            deathMessageText = playerName + " was " + deathMessageText;
            Component modifiedComponent = LegacyComponentSerializer.legacySection().deserialize(deathMessageText);
            event.setDeathText(modifiedComponent);
            org.wrench.Main.log("info", deathMessageText);
        });
    }
}
