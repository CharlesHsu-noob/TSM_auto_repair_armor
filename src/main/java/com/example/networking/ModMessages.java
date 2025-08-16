package com.example.networking;

import com.example.RepairArmorV2;
import net.minecraft.util.Identifier;

public class ModMessages {
    public static final Identifier UNEQUIP_ARMOR = Identifier.of("repairarmor", "unequip_armor");

    public static void registerC2SPackets() {
        // Register packet types
        RepairArmorC2SPacket.register();
        UnequipArmorC2SPacket.register();
        
        RepairArmorV2.LOGGER.info("Registered C2S packets for " + RepairArmorV2.MOD_ID);
    }
    
    public static void registerS2CPackets() {
        // Register any client-bound packets here if needed
    }
}
