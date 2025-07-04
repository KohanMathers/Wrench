package org.wrench.commands;

import org.wrench.commands.playerManagement.PlayerManagementCommands;
import org.wrench.commands.wrenchDefault.WrenchDefaultCommands;

import net.minestom.server.command.CommandManager;

public class CommandRegistry {
    public static void registerAll(CommandManager commandManager) {
        PlayerManagementCommands.register(commandManager);
        WrenchDefaultCommands.register(commandManager);
    }
}
   