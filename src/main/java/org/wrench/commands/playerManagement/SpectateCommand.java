package org.wrench.commands.playerManagement;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;

public class SpectateCommand extends Command {
    public SpectateCommand() {
        super("spectate");

        setDefaultExecutor((sender, context) -> {
            if (sender instanceof Player player) {
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    player.spectate(player);
                } else {
                    sender.sendMessage(Component.text("Usage: /spectate <target> [player]", NamedTextColor.RED));
                }
            } else {
                if (sender != null) {
                    sender.sendMessage(Component.text("Usage: /spectate <target> [player]", NamedTextColor.RED));
                }
            }
        });

        var playerArg = ArgumentType.Entity("player").singleEntity(true).onlyPlayers(true);
        var targetPlayerArg = ArgumentType.Entity("target").singleEntity(true).onlyPlayers(true);

        addSyntax((sender, context) -> {
            var entityFinder = context.get(playerArg);
            var targetPlayer = entityFinder.findFirstPlayer(sender);

            if (targetPlayer == null) {
                sender.sendMessage(Component.text("Target must be a player", NamedTextColor.RED));
                return;
            }

            if (sender instanceof Player player) {
                player.setGameMode(GameMode.SPECTATOR);
                player.spectate(targetPlayer);
            } else {
                if (sender != null) {
                    sender.sendMessage(Component.text("This command can only be used by players", NamedTextColor.RED));
                }
            }
        }, playerArg);

        addSyntax((sender, context) -> {
            var sourceFinder = context.get(playerArg);
            var targetFinder = context.get(targetPlayerArg);

            Player sourcePlayer = sourceFinder.findFirstPlayer(sender);
            Player targetPlayer = targetFinder.findFirstPlayer(sender);

            if (sourcePlayer == null || targetPlayer == null) {
                sender.sendMessage(Component.text("Both source and target must be players", NamedTextColor.RED));
                return;
            }

            sourcePlayer.setGameMode(GameMode.SPECTATOR);
            sourcePlayer.spectate(targetPlayer);
        }, playerArg, targetPlayerArg);
    }
}