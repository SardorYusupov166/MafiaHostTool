package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.GamePlayer;
import com.mlpgod.mafiatool.model.GameRecord;
import com.mlpgod.mafiatool.model.RoleDefinition;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class RoleChangeScreen extends Screen {
    private final GameRecord game; private final GamePlayer player; private int scroll;
    public RoleChangeScreen(GameRecord game, GamePlayer player) { super(Component.literal("Изменить роль")); this.game = game; this.player = player; }
    @Override protected void init() {
        var roles = GameService.get().roleTemplates();
        int visible = Math.max(1, (height - 120) / 30); scroll = Math.min(scroll, Math.max(0, roles.size() - visible));
        int left = width / 2 - 170;
        for (int i = scroll; i < roles.size() && i < scroll + visible; i++) {
            RoleDefinition role = roles.get(i); int y = 58 + (i - scroll) * 30;
            addRenderableWidget(Button.builder(Component.literal(role.name + " · " + role.team), b -> change(role)).bounds(left, y, 340, 24).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> Minecraft.getInstance().setScreen(new MafiaGameScreen(game))).bounds(left, height - 42, 100, 24).build());
    }
    private void change(RoleDefinition role) {
        Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
            if (yes) try { GameService.get().changePlayerRole(game, player.id, role, "Изменение роли ведущим"); } catch (Exception ignored) {}
            Minecraft.getInstance().setScreen(new MafiaGameScreen(game));
        }, Component.literal("Изменить роль игрока?"), Component.literal("Старая роль останется в журнале."), Component.literal("Изменить"), Component.literal("Отмена")));
    }
    @Override public void render(GuiGraphics g, int x, int y, float d) {
        g.fill(0, 0, width, height, 0xA0101010); g.drawCenteredString(font, "Новая роль для " + player.name, width / 2, 22, 0xFFFFFF); super.render(g, x, y, d);
    }
    @Override public boolean isPauseScreen() { return false; }
}
