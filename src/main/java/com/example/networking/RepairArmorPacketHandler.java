package com.example.networking;

import com.example.RepairArmorV2;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class RepairArmorPacketHandler {
    private static final Text TITLE = Text.translatable("container.repair");
    
    // Public entry point for server-side triggers (e.g., commands)
    public static void triggerRepair(ServerPlayerEntity player) {
        RepairArmorV2.LOGGER.debug("Triggering repair for: {}", player.getName().getString());
        processRepair(player);
    }
    
    public static void receive(RepairArmorC2SPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        player.getServer().execute(() -> {
            processRepair(player);
        });
        RepairArmorV2.LOGGER.info("SERVER: Received repair armor packet from " + player.getName().getString());
    }
    
    private static void processRepair(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        RepairArmorV2.LOGGER.debug("Processing repair for player: {}", player.getName().getString());
        
        // Try to find an armor piece that needs repair
        for (int i = 0; i < 4; i++) {
            ItemStack armorStack = inventory.getArmorStack(i);
            if (!armorStack.isEmpty() && armorStack.isDamaged()) {
                // Found armor that needs repair
                RepairArmorV2.LOGGER.debug("Found damaged armor in slot {}: {} (damage {} of {})", i, armorStack.getName().getString(), armorStack.getDamage(), armorStack.getMaxDamage());
                repairArmorPiece(player, i);
                return;
            }
            else {
                RepairArmorV2.LOGGER.trace("Checked armor slot {}: empty={}, damaged={}", i, armorStack.isEmpty(), armorStack.isDamaged());
            }
        }
        
        // If we get here, no armor needs repair
        RepairArmorV2.LOGGER.debug("No damaged armor found for player: {}", player.getName().getString());
        player.sendMessage(Text.literal("No damaged armor found."), false);
    }
    
    private static void repairArmorPiece(ServerPlayerEntity player, int armorSlot) {
        PlayerInventory inventory = player.getInventory();
        ItemStack armorStack = inventory.getArmorStack(armorSlot).copy();
        
        if (armorStack.isEmpty()) return;
        
        // Create a simple anvil screen handler
        RepairArmorV2.LOGGER.debug("Opening anvil UI to repair slot {} for player {}", armorSlot, player.getName().getString());
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInventory, playerEntity) -> 
            new AnvilScreenHandler(syncId, playerInventory, 
                ScreenHandlerContext.create(player.getWorld(), player.getBlockPos())) {
                
                @Override
                public void onContentChanged(Inventory inventory) {
                    super.onContentChanged(inventory);
                    
                    Slot outputSlot = this.getSlot(2);
                    if (outputSlot == null) return;
                    
                    ItemStack output = outputSlot.getStack();
                    if (!output.isEmpty()) {
                        if (output.isDamaged()) {
                            // If still damaged, try to repair again
                            Slot inputSlot = this.getSlot(0);
                            if (inputSlot != null) {
                                inputSlot.setStack(output.copy());
                                Slot materialSlot = this.getSlot(1);
                                if (materialSlot != null) {
                                    materialSlot.setStack(ItemStack.EMPTY);
                                }
                            }
                        } else {
                            // If repaired, equip it back
                            EquipmentSlot slot = getEquipmentSlotForArmor(armorSlot);
                            player.equipStack(slot, output);
                            // Close the screen on the next tick to avoid concurrent modification
                            player.getServer().execute(() -> {
                                if (player.currentScreenHandler != null) {
                                    // Close the screen by scheduling it on the server thread
                                    player.getServer().execute(() -> {
                                        if (player.currentScreenHandler != null) {
                                            // Use reflection to access the protected method
                                            try {
                                                var method = PlayerEntity.class.getDeclaredMethod("closeHandledScreen");
                                                method.setAccessible(true);
                                                method.invoke(player);
                                            } catch (Exception e) {
                                                RepairArmorV2.LOGGER.error("Failed to close screen", e);
                                            }
                                        }
                                    });
                                }
                            });
                        }
                    }
                }
                
                @Override
                public void onClosed(PlayerEntity player) {
                    super.onClosed(player);
                    // Process next armor piece when the anvil is closed
                    if (player instanceof ServerPlayerEntity serverPlayer) {
                        serverPlayer.getServer().execute(() -> 
                            processRepair(serverPlayer)
                        );
                    }
                }
                
                @Override
                public boolean canUse(PlayerEntity player) {
                    return true;
                }
            },
            TITLE
        ));
        
        // Put the armor in the first slot of the anvil
        if (player.currentScreenHandler instanceof ForgingScreenHandler handler) {
            Slot inputSlot = handler.getSlot(0);
            if (inputSlot != null) {
                inputSlot.setStack(armorStack);
            }
        }
        
        // Notify the player
        player.sendMessage(Text.literal("Repairing " + armorStack.getName().getString()), false);
    }
    
    private static EquipmentSlot getEquipmentSlotForArmor(int armorSlot) {
        return switch (armorSlot) {
            case 0 -> EquipmentSlot.FEET;     // Boots
            case 1 -> EquipmentSlot.LEGS;     // Leggings
            case 2 -> EquipmentSlot.CHEST;    // Chestplate
            case 3 -> EquipmentSlot.HEAD;     // Helmet
            default -> throw new IllegalArgumentException("Invalid armor slot: " + armorSlot);
        };
    }
    
}
