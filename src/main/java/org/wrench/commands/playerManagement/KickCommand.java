package org.wrench.commands.playerManagement;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;

public class KickCommand extends Command {
    public KickCommand() {
        super("kick");

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("Usage: /kick <player> [reason]", NamedTextColor.RED));
        });

        var playerArg = ArgumentType.Entity("player").singleEntity(true).onlyPlayers(true);
        var reasonArg = ArgumentType.StringArray("reason").setDefaultValue(new String[] {"You have been kicked from the server"});

        addSyntax((sender, context) -> {
            var entityFinder = context.get(playerArg);
            var targetPlayer = entityFinder.findFirstPlayer(sender);

            if (targetPlayer == null) {
                sender.sendMessage(Component.text("Target must be a player", NamedTextColor.RED));
                return;
            }

            String[] reasonArray = context.get(reasonArg);
            String reason = String.join(" ", reasonArray);
            targetPlayer.kick(reason);
            sender.sendMessage(Component.text("Kicked " + targetPlayer.getUsername(), NamedTextColor.GREEN));
        }, playerArg, reasonArg);
    }
}
