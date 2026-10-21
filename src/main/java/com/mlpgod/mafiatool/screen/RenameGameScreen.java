package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.GameRecord;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class RenameGameScreen extends Screen {
    private final GameRecord game;
    private final Screen returnTo;
    private EditBox name;
    private String error = "";

    public RenameGameScreen(GameRecord game, Screen returnTo) {
        super(Component.literal("Переименовать партию"));
        this.game = game; this.returnTo = returnTo;
    }

    @Override protected void init() {
        int left = width / 2 - 150;
        name = new EditBox(font, left, height / 2 - 8, 300, 20, Component.literal("Название"));
        name.setMaxLength(48); name.setValue(game.title); addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> save()).bounds(left, height / 2 + 22, 120, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> Minecraft.getInstance().setScreen(returnTo)).bounds(left + 128, height / 2 + 22, 100, 22).build());
    }

    private void save() {
        try { GameService.get().renameGame(game, name.getValue()); Minecraft.getInstance().setScreen(returnTo); }
        catch (Exception e) { error = e.getMessage(); }
    }

    @Override public void render(GuiGraphics g, int x, int y, float delta) {
        g.fill(0, 0, width, height, 0xA0101010);
        g.drawCenteredString(font, "Переименовать партию", width / 2, height / 2 - 42, 0xFFFFFF);
        if (!error.isBlank()) g.drawCenteredString(font, error, width / 2, height / 2 + 54, 0xFF8888);
        super.render(g, x, y, delta);
    }
    @Override public boolean isPauseScreen() { return false; }
}
