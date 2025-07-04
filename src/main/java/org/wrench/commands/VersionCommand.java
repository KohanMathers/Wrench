package org.wrench.commands;

import net.minestom.server.command.builder.Command;

public class VersionCommand extends Command {
    public VersionCommand() {
        super("version", "ver", "wrench");

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage("This server is running Wrench version 1.0-DEV");
        });
    }
}