package org.wrench.commands.playerManagement;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;

public class GamemodeCommand extends Command {
    public GamemodeCommand() {
        super("gamemode");

        var gamemodeArg = ArgumentType.String("gamemode");
        gamemodeArg.setSuggestionCallback((sender, context, suggestion) -> {
            for (GameMode gm : GameMode.values()) {
                suggestion.addEntry(new SuggestionEntry (gm.name().toLowerCase()));
            }
        });

        var targetArg = ArgumentType.String("target");
        targetArg.setSuggestionCallback((sender, context, suggestion) -> {
            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                suggestion.addEntry(new SuggestionEntry (player.getUsername()));
            }
        });


        addSyntax((sender, context) -> {
            if (!(sender instanceof Player)) {
                if (sender != null) {
                    sender.sendMessage("This command can only be used by players");
                }
                return;
            }

            Player player = (Player) sender;
            String gamemodeString = context.get(gamemodeArg);

            try {
                GameMode gamemode = GameMode.valueOf(gamemodeString.toUpperCase());
                String formattedGamemode = formatGameMode(gamemode);
                player.setGameMode(gamemode);
                player.sendMessage("Set own game mode to " + formattedGamemode + " Mode");
            } catch (IllegalArgumentException e) {
                player.sendMessage(Component.text("Invalid game mode: " + gamemodeString, NamedTextColor.RED));
            }
        }, gamemodeArg);

        addSyntax((sender, context) -> {
            Player commandSender = (Player) sender;
            String targetString = context.get(targetArg);
            String gamemodeString = context.get(gamemodeArg);

            Player targetPlayer = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetString);

            if (targetPlayer == null) {
                commandSender.sendMessage(Component.text("No player was found", NamedTextColor.RED));
                return;
            }

            try {
                GameMode gamemode = GameMode.valueOf(gamemodeString.toUpperCase());
                String formattedGamemode = formatGameMode(gamemode);
                targetPlayer.setGameMode(gamemode);
                targetPlayer.sendMessage("Your game mode has been updated to " + formattedGamemode + " Mode");
                commandSender.sendMessage("Set " + targetPlayer.getUsername() + "'s game mode to " + formattedGamemode + " Mode");
            } catch (IllegalArgumentException e) {
                commandSender.sendMessage(Component.text("Invalid game mode: " + gamemodeString, NamedTextColor.RED));
            }
        }, gamemodeArg, targetArg);
    }

    private String formatGameMode(GameMode gamemode) {
        String lower = gamemode.name().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
