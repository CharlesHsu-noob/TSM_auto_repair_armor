package com.example;

import com.example.key.KeyBindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;
//import java.util.function.Consumer;

public class RepairArmorV2Client implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("repair-armor-client");
    private static final Queue<DelayedTask> taskQueue = new ArrayDeque<>();
    private static long nextExecuteTime = 0;
    
    @Override
    public void onInitializeClient() {
        KeyBindings.register();
        registerKeyInputHandler();
        registerTickHandler();
    }
    
    private void registerTickHandler() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (taskQueue.isEmpty()) return;
            
            long currentTime = System.currentTimeMillis();
            if (currentTime >= nextExecuteTime) {
                DelayedTask task = taskQueue.poll();
                if (task != null) {
                    task.run();
                    nextExecuteTime = currentTime + (task.delayTicks * 50); // 轉換為毫秒
                }
            }
        });
    }
    
    private static void scheduleTask(Runnable task, int delayTicks) {
        taskQueue.add(new DelayedTask(task, delayTicks));
    }
    
    private static class DelayedTask {
        final Runnable task;
        final int delayTicks;
        
        DelayedTask(Runnable task, int delayTicks) {
            this.task = task;
            this.delayTicks = delayTicks;
        }
        
        void run() {
            task.run();
        }
    }

    private void registerKeyInputHandler() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (KeyBindings.unequipArmorKey.wasPressed()) {
                if (client.player != null && client.interactionManager != null) {
                    LOGGER.debug("Key pressed: unequip armor for {}", client.player.getName().getString());
                    unequipArmor(client.player, client.interactionManager);
                }
            }
        });
    }

    private void scheduleWithRandomDelay(Runnable task) {
        // 隨機延遲 10-50 毫秒 (約 1-3 tick)
        int randomTicks = 1 + (int)(Math.random() * 3); // 1-3 ticks (20-60ms)
        scheduleTask(task, randomTicks);
    }

    private void processArmorPiece(ClientPlayerEntity player, ClientPlayerInteractionManager interactionManager, int slotIndex, Runnable onComplete) {
        PlayerInventory inventory = player.getInventory();
        ItemStack armorStack = inventory.getArmorStack(slotIndex);
        
        if (armorStack.isEmpty()) {
            LOGGER.trace("Skipping empty armor slot {}", slotIndex);
            onComplete.run();
            return;
        }

        LOGGER.trace("Processing armor slot {}: {}", slotIndex, armorStack.getName().getString());
        int emptyHotbarSlot = findEmptyHotbarSlot(inventory);
        
        if (emptyHotbarSlot == -1) {
            LOGGER.debug("Hotbar full while trying to move armor from slot {}", slotIndex);
            // 若快捷欄滿，仍然嘗試發送一次 /repair
            if (player.networkHandler != null) {
                try {
                    player.networkHandler.sendChatCommand("repair");
                } catch (Throwable t) {
                    player.networkHandler.sendChatMessage("/repair");
                }
                LOGGER.debug("Hotbar full - still sent /repair once for slot {}", slotIndex);
            }
            onComplete.run();
            return;
        }

        int armorScreenSlot = 8 - slotIndex; // 5=helmet, 6=chest, 7=leggings, 8=boots

        // 1) 脫下：用 SWAP 把裝備放到快捷欄
        scheduleWithRandomDelay(() -> {
            interactionManager.clickSlot(
                player.playerScreenHandler.syncId,
                armorScreenSlot,
                emptyHotbarSlot,
                SlotActionType.SWAP,
                player
            );
            LOGGER.debug("[Step 1] Unequipped from armor slot {} to hotbar slot {}", slotIndex, emptyHotbarSlot);
            
            // 2) 發送聊天訊息 /repair
            scheduleWithRandomDelay(() -> {
                if (player.networkHandler != null) {
                    try {
                        player.networkHandler.sendChatCommand("repair");
                        LOGGER.debug("[Step 2] Sent chat command: /repair");
                    } catch (Throwable t) {
                        player.networkHandler.sendChatMessage("/repair");
                        LOGGER.debug("[Step 2] Sent chat message (fallback): /repair");
                    }
                }
                
                // 3) 穿回：再用 SWAP 把快捷欄的裝備換回盔甲槽
                scheduleWithRandomDelay(() -> {
                    interactionManager.clickSlot(
                        player.playerScreenHandler.syncId,
                        armorScreenSlot,
                        emptyHotbarSlot,
                        SlotActionType.SWAP,
                        player
                    );
                    LOGGER.debug("[Step 3] Re-equipped from hotbar slot {} back to armor slot {}", emptyHotbarSlot, slotIndex);
                    
                    // 處理下一個裝備槽
                    onComplete.run();
                });
            });
        });
    }

    private void unequipArmor(ClientPlayerEntity player, ClientPlayerInteractionManager interactionManager) {
        // 創建一個陣列來追蹤處理狀態
        //boolean[] processed = new boolean[4]; // 4個裝備槽
        boolean hotbarFull = false;
        boolean movedAny = false;

        // 遞迴處理每個裝備槽
        processArmorPiece(player, interactionManager, 3, () -> {
            processArmorPiece(player, interactionManager, 2, () -> {
                processArmorPiece(player, interactionManager, 1, () -> {
                    processArmorPiece(player, interactionManager, 0, () -> {
                        // 所有裝備處理完成
                        player.sendMessage(Text.literal("All armor pieces processed."), true);
                        LOGGER.debug("All armor pieces processed");
                    });
                });
            });
        });

        if (movedAny) {
            player.sendMessage(Text.literal("已將盔甲移至快捷欄。"), true);
            LOGGER.debug("卸下盔甲完成：已將物品移至快捷欄");
        } else if (hotbarFull) {
            player.sendMessage(Text.literal("快捷欄已滿，無法卸下盔甲。"), true);
            LOGGER.debug("卸下盔甲中止：快捷欄已滿");
        } else {
            player.sendMessage(Text.literal("沒有可卸下的盔甲。"), true);
            LOGGER.debug("跳過卸下盔甲：未裝備任何盔甲");
        }
    }

    private int findEmptyHotbarSlot(PlayerInventory inventory) {
        for (int j = 0; j < 9; j++) {
            if (inventory.getStack(j).isEmpty()) {
                return j;
            }
        }
        return -1;
    }
}
