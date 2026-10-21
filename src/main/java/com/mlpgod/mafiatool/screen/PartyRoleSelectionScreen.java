package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.GameRecord;
import com.mlpgod.mafiatool.model.RoleSelection;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class PartyRoleSelectionScreen extends Screen {
    private final GameRecord game; private final Screen returnTo;
    private final GameService service = GameService.get();
    private int scroll; private String message = "";
    public PartyRoleSelectionScreen(GameRecord game, Screen returnTo) { super(Component.literal("Роли партии")); this.game = game; this.returnTo = returnTo; }

    @Override protected void init() {
        List<RoleSelection> roles = game.rolePlan;
        int left = Math.max(14, width / 2 - 250);
        int visible = Math.max(1, Math.min(11, (height - 148) / 28));
        scroll = Math.min(scroll, Math.max(0, roles.size() - visible));
        for (int i = scroll; i < roles.size() && i < scroll + visible; i++) {
            RoleSelection role = roles.get(i);
            int y = 68 + (i - scroll) * 28;
            addRenderableWidget(Button.builder(Component.literal((role.enabled ? "[Вкл] " : "[Выкл] ") + role.role.name), b -> {
                boolean enabled = !role.enabled;
                try {
                    service.setGameRoleSelection(game, role.templateId, enabled, role.count);
                    b.setMessage(Component.literal((enabled ? "[Вкл] " : "[Выкл] ") + role.role.name));
                    message = "Настройки партии сохранены";
                } catch (Exception e) { message = e.getMessage(); }
            }).bounds(left, y, 250, 22).build());
            EditBox count = new EditBox(font, left + 258, y, 48, 20, Component.literal("Кол."));
            count.setMaxLength(2); count.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
            count.setValue(Integer.toString(role.count));
            count.setResponder(value -> {
                try {
                    if (!value.isBlank()) service.setGameRoleSelection(game, role.templateId, role.enabled, Integer.parseInt(value));
                } catch (Exception e) { message = e.getMessage(); }
            });
            addRenderableWidget(count);
            addRenderableWidget(Button.builder(Component.literal("Описание"), b -> message = role.role.description.isBlank() ? "Описание не задано" : role.role.description)
                    .bounds(left + 312, y, 78, 22).build());
        }
        int bottom = height - 40;
        addRenderableWidget(Button.builder(Component.literal("Добавить новые шаблоны"), b -> {
            try { service.addMissingRoleTemplates(game); message = "Список ролей обновлён"; rebuildWidgets(); }
            catch (Exception e) { message = e.getMessage(); }
        }).bounds(left, bottom, 166, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> Minecraft.getInstance().setScreen(returnTo))
                .bounds(width - 92, bottom, 74, 24).build());
        if (roles.size() > visible) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> { scroll = Math.max(0, scroll - 1); rebuildWidgets(); }).bounds(left + 396, 66, 22, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> { scroll = Math.min(roles.size() - visible, scroll + 1); rebuildWidgets(); }).bounds(left + 396, height - 67, 22, 20).build());
        }
    }

    @Override public void render(GuiGraphics g, int x, int y, float d) {
        g.fill(0, 0, width, height, 0xA0101010);
        g.drawCenteredString(font, "Роли партии · " + game.title, width / 2, 18, 0xFFFFFF);
        g.drawCenteredString(font, "Выбрано экземпляров: " + service.enabledRoleCount(game) + " / " + game.players.size(), width / 2, 40, 0xA0A0A0);
        List<RoleSelection> roles = game.rolePlan;
        int left = Math.max(14, width / 2 - 250);
        for (int i = scroll; i < roles.size() && i < scroll + Math.max(1, Math.min(11, (height - 148) / 28)); i++) {
            RoleSelection role = roles.get(i);
            g.drawString(font, role.role.team + " · " + role.role.actionPhase, left + 5, 90 + (i - scroll) * 28, 0x909090);
        }
        if (!message.isBlank()) g.drawString(font, message, left + 180, height - 58, 0xFFFFFF);
        super.render(g, x, y, d);
    }
    @Override public boolean isPauseScreen() { return false; }
}
