package com.mlpgod.mafiatool;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import com.mlpgod.mafiatool.model.GameRecord;
import com.mlpgod.mafiatool.overlay.MafiaOverlayManager;
import com.mlpgod.mafiatool.screen.MafiaGameScreen;
import com.mlpgod.mafiatool.screen.MafiaGamesScreen;
import org.lwjgl.glfw.GLFW;

public final class MafiaToolClient implements ClientModInitializer {
    private KeyMapping openScreenKey;
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("mafiatool", "main"));

    @Override
    public void onInitializeClient() {
        MafiaOverlayManager.initialize();
        openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mafiatool.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openScreenKey.consumeClick()) {
                if (client.screen != null && !(client.screen instanceof MafiaGameScreen)
                        && !(client.screen instanceof MafiaGamesScreen)) continue;
                boolean shift = GLFW.glfwGetKey(client.getWindow().handle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(client.getWindow().handle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                if (shift) {
                    client.setScreen(new MafiaGamesScreen());
                } else if (client.screen instanceof MafiaGameScreen || client.screen instanceof MafiaGamesScreen) {
                    client.setScreen(null); // HUD is separate and remains visible.
                } else if (client.screen == null) {
                    GameRecord active = MafiaOverlayManager.current();
                    if (active == null) client.setScreen(new MafiaGamesScreen());
                    else {
                        MafiaOverlayManager.show(active); // M restores a manually hidden panel.
                        client.setScreen(new MafiaGameScreen(active));
                    }
                }
                // Never eat M while the user is typing in chat or another input screen.
            }
        });
    }
}
