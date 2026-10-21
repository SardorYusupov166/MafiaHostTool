package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.GameRecord;
import com.mlpgod.mafiatool.model.PhaseKind;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PhaseCorrectionScreen extends Screen {
    private final GameRecord game; private final Screen returnTo;
    private PhaseKind kind; private int number; private EditBox reason; private String error = "";
    public PhaseCorrectionScreen(GameRecord game, Screen returnTo) {
        super(Component.literal("Исправить фазу")); this.game = game; this.returnTo = returnTo;
        kind = game.phase.kind == PhaseKind.NIGHT ? PhaseKind.NIGHT : PhaseKind.DAY; number = Math.max(1, game.phase.number);
    }
    @Override protected void init() {
        int left = width / 2 - 150;
        addRenderableWidget(Button.builder(Component.literal("Фаза: " + (kind == PhaseKind.DAY ? "День" : "Ночь")), b -> {
            kind = kind == PhaseKind.DAY ? PhaseKind.NIGHT : PhaseKind.DAY;
            b.setMessage(Component.literal("Фаза: " + (kind == PhaseKind.DAY ? "День" : "Ночь")));
        }).bounds(left, height / 2 - 50, 100, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Номер −"), b -> { number = Math.max(1, number - 1); b.setMessage(Component.literal("Номер − (" + number + ")")); })
                .bounds(left + 108, height / 2 - 50, 80, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Номер +"), b -> { number++; b.setMessage(Component.literal("Номер + (" + number + ")")); })
                .bounds(left + 194, height / 2 - 50, 80, 22).build());
        reason = new EditBox(font, left, height / 2 - 18, 300, 20, Component.literal("Причина исправления"));
        reason.setHint(Component.literal("Почему исправлена фаза?")); reason.setMaxLength(120); addRenderableWidget(reason);
        addRenderableWidget(Button.builder(Component.literal("Сохранить исправление"), b -> save()).bounds(left, height / 2 + 14, 164, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> Minecraft.getInstance().setScreen(returnTo)).bounds(left + 172, height / 2 + 14, 84, 22).build());
    }
    private void save() {
        try { GameService.get().correctPhase(game, kind, number, reason.getValue()); Minecraft.getInstance().setScreen(returnTo); }
        catch (Exception e) { error = e.getMessage(); }
    }
    @Override public void render(GuiGraphics g, int x, int y, float d) {
        g.fill(0, 0, width, height, 0xA0101010); g.drawCenteredString(font, "Текущая фаза: " + game.phase, width / 2, height / 2 - 82, 0xFFFFFF);
        if (!error.isBlank()) g.drawCenteredString(font, error, width / 2, height / 2 + 48, 0xFF8888); super.render(g, x, y, d);
    }
    @Override public boolean isPauseScreen() { return false; }
}
