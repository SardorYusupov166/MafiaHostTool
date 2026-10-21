package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.GameRecord;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class FinishGameScreen extends Screen {
    private final GameRecord game;
    private EditBox winner;
    private EditBox comment;
    private String error = "";

    public FinishGameScreen(GameRecord game) { super(Component.literal("Завершить партию")); this.game = game; }

    @Override protected void init() {
        int left = width / 2 - 170;
        winner = new EditBox(font, left, height / 2 - 35, 340, 20, Component.literal("Победившая команда"));
        winner.setHint(Component.literal("Победившая команда — можно оставить пустым")); winner.setMaxLength(80); addRenderableWidget(winner);
        comment = new EditBox(font, left, height / 2 - 6, 340, 20, Component.literal("Комментарий"));
        comment.setHint(Component.literal("Комментарий ведущего — необязательно")); comment.setMaxLength(200); addRenderableWidget(comment);
        addRenderableWidget(Button.builder(Component.literal("Завершить партию"), b -> confirm())
                .bounds(left, height / 2 + 28, 150, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> Minecraft.getInstance().setScreen(new MafiaGameScreen(game)))
                .bounds(left + 158, height / 2 + 28, 100, 22).build());
    }

    private void confirm() {
        Minecraft.getInstance().setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                try {
                    GameService.get().finishGame(game, winner.getValue(), comment.getValue());
                    Minecraft.getInstance().setScreen(new MafiaGamesScreen());
                    return;
                } catch (Exception e) { error = e.getMessage(); }
            }
            Minecraft.getInstance().setScreen(this);
        }, Component.literal("Завершить партию?"), Component.literal("Она попадёт в архив и станет доступна только для просмотра."), Component.literal("Завершить"), Component.literal("Назад")));
    }

    @Override public void render(GuiGraphics g, int x, int y, float delta) {
        g.fill(0, 0, width, height, 0xA0101010);
        g.drawCenteredString(font, "Результат партии", width / 2, height / 2 - 70, 0xFFFFFF);
        if (!error.isBlank()) g.drawCenteredString(font, error, width / 2, height / 2 + 60, 0xFF8888);
        super.render(g, x, y, delta);
    }
    @Override public boolean isPauseScreen() { return false; }
}
