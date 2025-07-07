package org.wrench.commands.playerManagement;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

public class TeleportCommand extends Command {
    public TeleportCommand() {
        super("teleport", "tp");

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("Usage: /tp [subject] <target>", NamedTextColor.RED));
        });

        var xCoordArg = ArgumentType.Float("x");
        var yCoordArg = ArgumentType.Float("y");
        var zCoordArg = ArgumentType.Float("z");
        var playerArg = ArgumentType.Entity("player").singleEntity(true).onlyPlayers(true);
        var targetPlayerArg = ArgumentType.Entity("target").singleEntity(true).onlyPlayers(true);

        addSyntax((sender, context) -> {
            float x = context.get(xCoordArg);
            float y = context.get(yCoordArg);
            float z = context.get(zCoordArg);

            if (sender instanceof Player player) {
                player.teleport(new Pos(x, y, z));
                sender.sendMessage("Teleported " + player.getUsername() + " to " + x + ", " + y + ", " + z);
            } else {
                if (sender != null) {
                    sender.sendMessage(Component.text("This command can only be used by players", NamedTextColor.RED));
                }
            }
        }, xCoordArg, yCoordArg, zCoordArg);

        addSyntax((sender, context) -> {
            var entityFinder = context.get(playerArg);
            Player target = entityFinder.findFirstPlayer(sender);

            if (target == null) {
                sender.sendMessage(Component.text("Target must be a player", NamedTextColor.RED));
                return;
            }

            if (sender instanceof Player player) {
                player.teleport(target.getPosition());
                sender.sendMessage("Teleported " + player.getUsername() + " to " + target.getUsername());
            } else {
                if (sender != null) {
                    sender.sendMessage(Component.text("This command can only be used by players", NamedTextColor.RED));
                }
            }
        }, playerArg);

        addSyntax((sender, context) -> {
            var sourceFinder = context.get(playerArg);
            var targetFinder = context.get(targetPlayerArg);

            Player source = sourceFinder.findFirstPlayer(sender);
            if (source == null) {
                sender.sendMessage(Component.text("Source must be a player", NamedTextColor.RED));
                return;
            }

            Player target = targetFinder.findFirstPlayer(sender);
            if (target == null) {
                sender.sendMessage(Component.text("Target must be a player", NamedTextColor.RED));
                return;
            }

            source.teleport(target.getPosition());
            sender.sendMessage("Teleported " + source.getUsername() + " to " + target.getUsername());
            
        }, playerArg, targetPlayerArg);

        addSyntax((sender, context) -> {
            var entityFinder = context.get(playerArg);
            float x = context.get(xCoordArg);
            float y = context.get(yCoordArg);
            float z = context.get(zCoordArg);

            Player target = entityFinder.findFirstPlayer(sender);
            if (target == null) {
                sender.sendMessage(Component.text("Target must be a player", NamedTextColor.RED));
                return;
            }

            target.teleport(new Pos(x, y, z));
            sender.sendMessage("Teleported " + target.getUsername() + " to " + x + ", " + y + ", " + z);

        }, playerArg, xCoordArg, yCoordArg, zCoordArg);
    }
}