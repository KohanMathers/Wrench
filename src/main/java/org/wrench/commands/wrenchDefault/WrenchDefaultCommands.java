package org.wrench.commands.wrenchDefault;

import net.minestom.server.command.CommandManager;

public class WrenchDefaultCommands {
    public static void register(CommandManager commandManager) {
        commandManager.register(new VersionCommand());
    }
}