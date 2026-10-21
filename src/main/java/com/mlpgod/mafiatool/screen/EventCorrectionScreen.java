package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.GameEvent;
import com.mlpgod.mafiatool.model.GameRecord;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class EventCorrectionScreen extends Screen {
    private final GameRecord game; private final GameEvent original; private final Screen returnTo;
    private EditBox result; private EditBox reason; private String error = "";
    public EventCorrectionScreen(GameRecord game, GameEvent original, Screen returnTo) {
        super(Component.literal("Исправить событие")); this.game = game; this.original = original; this.returnTo = returnTo;
    }
    @Override protected void init() {
        int left = width / 2 - 180;
        result = field(left, height / 2 - 34, 360, "Корректное описание / результат", "", 180);
        reason = field(left, height / 2 - 5, 360, "Причина исправления", "", 120);
        addRenderableWidget(Button.builder(Component.literal("Добавить исправление"), b -> save()).bounds(left, height / 2 + 28, 168, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> Minecraft.getInstance().setScreen(returnTo)).bounds(left + 176, height / 2 + 28, 84, 22).build());
    }
    private EditBox field(int x, int y, int w, String hint, String value, int max) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(hint)); box.setHint(Component.literal(hint)); box.setMaxLength(max); box.setValue(value); addRenderableWidget(box); return box;
    }
    private void save() {
        try {
            GameService.get().correctEvent(game, original.id, result.getValue(), reason.getValue());
            Minecraft.getInstance().setScreen(returnTo);
        } catch (Exception e) { error = e.getMessage(); }
    }
    @Override public void render(GuiGraphics g, int x, int y, float d) {
        g.fill(0, 0, width, height, 0xA0101010);
        g.drawCenteredString(font, "Исправление добавится новой строкой; оригинал останется", width / 2, height / 2 - 70, 0xFFFFFF);
        g.drawCenteredString(font, original.description, width / 2, height / 2 - 52, 0xA0A0A0);
        if (!error.isBlank()) g.drawCenteredString(font, error, width / 2, height / 2 + 60, 0xFF8888); super.render(g, x, y, d);
    }
    @Override public boolean isPauseScreen() { return false; }
}
