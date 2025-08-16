package com.example.networking;

import com.example.RepairArmorV2;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class UnequipArmorPacketHandler {
    
    public static void receive(UnequipArmorC2SPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        player.getServer().execute(() -> {
            RepairArmorV2.LOGGER.info("SERVER: Received unequip packet from " + player.getName().getString());
            RepairArmorV2.LOGGER.info("SERVER: Processing unequip request for " + player.getName().getString());
            PlayerInventory inventory = player.getInventory();
            boolean movedAny = false;
            boolean hotbarFull = false;

            // Process armor slots from helmet to boots (3 to 0)
            for (int i = 3; i >= 0; i--) {
                ItemStack armorStack = inventory.getArmorStack(i);
                if (!armorStack.isEmpty()) {
                    // Check if armor has binding curse using JJElytraSwapInit method
                    if (hasBindingCurse(armorStack, player)) {
                        continue; // Skip cursed armor
                    }

                    int emptyHotbarSlot = findEmptyHotbarSlot(inventory);
                    
                    if (emptyHotbarSlot != -1) {
                        // Use JJElytraSwapInit-style swap method
                        swapArmorToHotbar(player, i, emptyHotbarSlot);
                        movedAny = true;
                    } else {
                        hotbarFull = true;
                        break;
                    }
                }
            }

            if (movedAny) {
                player.sendMessage(Text.literal("Armor unequipped to hotbar."), false);
            } else if (hotbarFull) {
                player.sendMessage(Text.literal("Hotbar is full, cannot unequip armor."), false);
            } else {
                player.sendMessage(Text.literal("No armor to unequip."), false);
            }
        });
    }

    // Check for binding curse using JJElytraSwapInit method
    private static boolean hasBindingCurse(ItemStack stack, ServerPlayerEntity player) {
        try {
            Registry<Enchantment> enchantmentRegistry = player.getServerWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            var bindingCurse = enchantmentRegistry.get(Enchantments.BINDING_CURSE);
            if (bindingCurse != null) {
                RegistryEntry<Enchantment> enchantEntry = enchantmentRegistry.getEntry(bindingCurse);
                return EnchantmentHelper.getLevel(enchantEntry, stack) > 0;
            }
        } catch (Exception e) {
            // If we can't check for binding curse, assume it's safe
        }
        return false;
    }

    private static int findEmptyHotbarSlot(PlayerInventory inventory) {
        for (int j = 0; j < 9; j++) {
            if (inventory.getStack(j).isEmpty()) {
                return j;
            }
        }
        return -1;
    }

    // Swap method using QuickArmorSwap logic with ClickSlot packet simulation
    private static void swapArmorToHotbar(ServerPlayerEntity player, int armorSlotIndex, int hotbarSlot) {
        PlayerInventory inventory = player.getInventory();
        
        try {
            // Get the armor item first
            ItemStack armorStack = inventory.getArmorStack(armorSlotIndex);
            
            if (!armorStack.isEmpty()) {
                // Calculate armor slot ID in screen handler (similar to QuickArmorSwap)
                // Armor slots: helmet=39, chestplate=38, leggings=37, boots=36
                int armorScreenSlot = 36 + armorSlotIndex;
                
                // Simulate the slot click action like QuickArmorSwap does
                // This uses the screen handler's slot management system
                player.currentScreenHandler.onSlotClick(
                    armorScreenSlot,  // armor slot
                    hotbarSlot,       // hotbar slot 
                    SlotActionType.SWAP,  // swap action
                    player
                );
                
                RepairArmorV2.LOGGER.info("Unequipped armor: {} from slot {} to hotbar slot {}", 
                    armorStack.getItem().toString(), 
                    armorSlotIndex,
                    hotbarSlot);
            }
                
        } catch (Exception ex) {
            RepairArmorV2.LOGGER.error("Error unequipping armor", ex);
        }
    }
}
