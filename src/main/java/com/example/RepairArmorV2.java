package com.example;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepairArmorV2 implements ModInitializer {
    public static final String MOD_ID = "repair-armor";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Repair Armor Mod (client-only)");
    }
}