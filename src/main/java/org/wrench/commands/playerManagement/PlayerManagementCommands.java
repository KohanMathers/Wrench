package org.wrench.commands.playerManagement;

import net.minestom.server.command.CommandManager;

public class PlayerManagementCommands {
    public static void register(CommandManager commandManager) {
        commandManager.register(new GamemodeCommand());
    }
}