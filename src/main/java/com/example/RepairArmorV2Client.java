package com.example;

import com.example.key.KeyBindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

public class RepairArmorV2Client implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("repair-armor-client");

    @Override
    public void onInitializeClient() {
        KeyBindings.register();
        registerKeyInputHandler();
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

    private void randomDelay() {
        try {
            // 10 to 50 ms inclusive upper bound handling
            Thread.sleep(ThreadLocalRandom.current().nextInt(10, 51));
        } catch (InterruptedException ignored) {
        }
    }

    private void unequipArmor(ClientPlayerEntity player, ClientPlayerInteractionManager interactionManager) {
        PlayerInventory inventory = player.getInventory();
        boolean movedAny = false;
        boolean hotbarFull = false;

        // Armor slots for getArmorStack: 3 (helmet), 2 (chestplate), 1 (leggings), 0 (boots)
        for (int i = 3; i >= 0; i--) {
            ItemStack armorStack = inventory.getArmorStack(i);
            if (!armorStack.isEmpty()) {
                LOGGER.trace("Checking armor slot {}: {}", i, armorStack.getName().getString());

                int emptyHotbarSlot = findEmptyHotbarSlot(inventory);
                
                if (emptyHotbarSlot != -1) {
                    // Player inventory screen handler slots: 5=helmet, 6=chest, 7=leggings, 8=boots
                    int armorScreenSlot = 8 - i;

                    // 1) 脫下：用 SWAP 把裝備放到快捷欄
                    interactionManager.clickSlot(
                        player.playerScreenHandler.syncId,
                        armorScreenSlot,
                        emptyHotbarSlot,
                        SlotActionType.SWAP,
                        player
                    );
                    LOGGER.debug("[Step 1] Unequipped from armor slot {} to hotbar slot {}", i, emptyHotbarSlot);
                    randomDelay();

                    // 2) 發送聊天訊息 /repair
                    if (player.networkHandler != null) {
                        try {
                            // 嘗試以指令發送（不含斜線）
                            player.networkHandler.sendChatCommand("repair");
                            LOGGER.debug("[Step 2] Sent chat command: /repair");
                        } catch (Throwable t) {
                            // 回退為純聊天訊息（含斜線）
                            player.networkHandler.sendChatMessage("/repair");
                            LOGGER.debug("[Step 2] Sent chat message (fallback): /repair");
                        }
                    }
                    randomDelay();

                    // 3) 穿回：再用 SWAP 把快捷欄的裝備換回盔甲槽
                    interactionManager.clickSlot(
                        player.playerScreenHandler.syncId,
                        armorScreenSlot,
                        emptyHotbarSlot,
                        SlotActionType.SWAP,
                        player
                    );
                    LOGGER.debug("[Step 3] Re-equipped from hotbar slot {} back to armor slot {}", emptyHotbarSlot, i);

                    movedAny = true;
                } else {
                    hotbarFull = true;
                    LOGGER.debug("Hotbar full while trying to move armor from slot {}", i);
                    // 若快捷欄滿，仍然嘗試發送一次 /repair 以符合需求（可選）
                    if (player.networkHandler != null) {
                        try {
                            player.networkHandler.sendChatCommand("repair");
                        } catch (Throwable t) {
                            player.networkHandler.sendChatMessage("/repair");
                        }
                        LOGGER.debug("Hotbar full - still sent /repair once for slot {}", i);
                    }
                    // 不中斷，繼續檢查下一個裝備槽，確保總共嘗試四次
                }
                // 每個裝備槽之間也加入一個小延遲
                randomDelay();
            }
        }

        if (movedAny) {
            player.sendMessage(Text.literal("Armor unequipped to hotbar."), true);
            LOGGER.debug("Armor unequip completed: moved items to hotbar");
        } else if (hotbarFull) {
            player.sendMessage(Text.literal("Hotbar is full, cannot unequip armor."), true);
            LOGGER.debug("Armor unequip aborted: hotbar is full");
        } else {
            player.sendMessage(Text.literal("No armor to unequip."), true);
            LOGGER.debug("Armor unequip skipped: no armor equipped");
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
