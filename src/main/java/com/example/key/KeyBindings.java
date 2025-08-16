package com.example.key;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static KeyBinding unequipArmorKey;

    public static void register() {
        unequipArmorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.repair-armor.unequip_armor", // The translation key of the keybinding's name
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V, // The default key
                "category.repair-armor.main" // The translation key of the keybinding's category
        ));
    }
}
