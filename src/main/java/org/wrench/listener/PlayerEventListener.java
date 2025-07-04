package org.wrench.listener;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.item.PickupItemEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerDeathEvent;
import net.minestom.server.item.ItemStack;

public final class PlayerEventListener {

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

        handler.addListener(PlayerBlockBreakEvent.class, event -> {
            if (event.getPlayer().getGameMode() == GameMode.SURVIVAL || event.getPlayer().getGameMode() == GameMode.ADVENTURE ) {
                var random = ThreadLocalRandom.current();

                var block = event.getBlock();
                var material = block.registry().material();
                if (material != null) {
                    var itemStack = ItemStack.of(material);
                    var itemEntity = new ItemEntity(itemStack);

                    var blockPos = event.getBlockPosition();
                    var dropPosition = blockPos.add(0.5, 0.5, 0.5);

                    itemEntity.setInstance(event.getInstance(), dropPosition);
                    itemEntity.setPickupDelay(Duration.ofMillis(500));

                    itemEntity.spawn();

                    // According to u/footstuff - "Y is always 4 m/s. X and Z are chosen uniformly between -2 and 2 m/s."
                    itemEntity.setVelocity(new Vec(
                        (random.nextDouble() - 0.5) * 4.0,
                        2.0,
                        (random.nextDouble() - 0.5) * 4.0
                    ));
                }
            }
        });

        handler.addListener(PickupItemEvent.class, event -> {
            var itemStack = event.getItemStack();

            if(event.getLivingEntity() instanceof Player player) {
                player.getInventory().addItemStack(itemStack);
            }
        });

        handler.addListener(ItemDropEvent.class, event -> {
            var itemStack = event.getItemStack();
            var player = event.getPlayer();
            var playerPos = player.getPosition();
            
            var itemEntity = new ItemEntity(itemStack);
            
            var eyePos = playerPos.add(0, player.getEyeHeight() - 0.3, 0);
            var direction = player.getPosition().direction();
            var dropPos = eyePos.add(direction.mul(0.3));
            
            itemEntity.setInstance(event.getInstance(), dropPos);
            
            var random = new Random();
            
            var forwardVel = direction.mul(5);
            
            var randomX = (random.nextDouble() - 0.5) * 0.5;
            var randomZ = (random.nextDouble() - 0.5) * 0.5;
            
            var upwardVel = 0.2 + random.nextDouble() * 0.1;
            
            var finalVelocity = forwardVel.add(randomX, upwardVel, randomZ);
            itemEntity.setVelocity(finalVelocity);
            
            itemEntity.setPickupDelay(Duration.ofMillis(500));
        });
    }
}
