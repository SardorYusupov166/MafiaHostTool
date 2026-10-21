package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.GameRecord;
import com.mlpgod.mafiatool.model.GamePlayer;
import com.mlpgod.mafiatool.model.PlayerStatus;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

public final class PlayerStatusScreen extends Screen {
    private final GameRecord game; private final GamePlayer player;
    private EditBox reason; private String error = "";
    public PlayerStatusScreen(GameRecord game, GamePlayer player) { super(Component.literal("Статус игрока")); this.game = game; this.player = player; }
    @Override protected void init() {
        int left = width / 2 - 160;
        reason = new EditBox(font, left, height / 2 - 34, 320, 20, Component.literal("Причина"));
        reason.setHint(Component.literal("Причина или пояснение (необязательно)")); reason.setMaxLength(120); addRenderableWidget(reason);
        addRenderableWidget(Button.builder(Component.literal("Жив"), b -> set(PlayerStatus.ALIVE)).bounds(left, height / 2, 76, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Мёртв"), b -> set(PlayerStatus.DEAD)).bounds(left + 82, height / 2, 76, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Исключён"), b -> set(PlayerStatus.EXCLUDED)).bounds(left + 164, height / 2, 92, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> Minecraft.getInstance().setScreen(new MafiaGameScreen(game))).bounds(left + 262, height / 2, 68, 22).build());
    }
    private void set(PlayerStatus status) {
        if (status != PlayerStatus.ALIVE && player.status != status) {
            Minecraft.getInstance().setScreen(new ConfirmScreen(yes -> {
                if (yes) apply(status);
                else Minecraft.getInstance().setScreen(this);
            }, Component.literal(status == PlayerStatus.DEAD ? "Отметить погибшим?" : "Исключить из партии?"), Component.literal(player.name + " останется в списке, а действие попадёт в журнал."), Component.literal("Подтвердить"), Component.literal("Отмена")));
            return;
        }
        apply(status);
    }
    private void apply(PlayerStatus status) {
        try { GameService.get().setPlayerStatus(game, player.id, status, reason.getValue()); Minecraft.getInstance().setScreen(new MafiaGameScreen(game)); }
        catch (Exception e) { error = e.getMessage(); }
    }
    @Override public void render(GuiGraphics g, int x, int y, float d) {
        g.fill(0, 0, width, height, 0xA0101010); g.drawCenteredString(font, "Статус: " + player.name, width / 2, height / 2 - 64, 0xFFFFFF);
        if (!error.isBlank()) g.drawCenteredString(font, error, width / 2, height / 2 + 32, 0xFF8888); super.render(g, x, y, d);
    }
    @Override public boolean isPauseScreen() { return false; }
}
