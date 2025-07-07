package org.wrench.commands.playerManagement;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;

import java.util.Collection;
import java.util.stream.Collectors;


public class ListCommand extends Command {
    public ListCommand() {
        super("list");

        setDefaultExecutor((sender, context) -> {
            Collection<Player> players = MinecraftServer.getConnectionManager().getOnlinePlayers();
            int currentPlayers = players.size();
            int maxPlayers = MinecraftServer.getConnectionManager().getOnlinePlayers().size() + 1;

            String playerList = players.stream()
                .map(Player::getUsername)
                .collect(Collectors.joining(", "));

            String message = String.format("There are %d of a max of %d players online: %s", 
                currentPlayers, maxPlayers, playerList);

            sender.sendMessage(Component.text(message, NamedTextColor.WHITE));
        });
    }
}