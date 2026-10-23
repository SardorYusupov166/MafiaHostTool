package com.mlpgod.mafiatool.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/** Vanilla chat input that returns the moderator to the floating game window when closed. */
final class ReturnToGameChatScreen extends ChatScreen {
    private final Screen returnTo;

    ReturnToGameChatScreen(Screen returnTo) {
        super("", false);
        this.returnTo = returnTo;
    }

    @Override public void onClose() {
        super.onClose();
        Minecraft.getInstance().setScreen(returnTo);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        boolean handled = super.keyPressed(event);
        if (Minecraft.getInstance().screen == null) Minecraft.getInstance().setScreen(returnTo);
        return handled;
    }

    @Override public boolean isPauseScreen() { return false; }
}
