package com.mlpgod.mafiatool;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import com.mlpgod.mafiatool.screen.MafiaGamesScreen;
import org.lwjgl.glfw.GLFW;

public final class MafiaToolClient implements ClientModInitializer {
    private KeyMapping openScreenKey;
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("mafiatool", "main"));

    @Override
    public void onInitializeClient() {
        openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mafiatool.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openScreenKey.consumeClick()) {
                if (client.screen instanceof MafiaGamesScreen) client.setScreen(null);
                else client.setScreen(new MafiaGamesScreen());
            }
        });
    }
}
