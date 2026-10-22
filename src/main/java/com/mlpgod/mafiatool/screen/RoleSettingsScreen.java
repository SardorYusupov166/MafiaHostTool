package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.RoleDefinition;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.util.List;

public final class RoleSettingsScreen extends Screen {
    private final GameService service = GameService.get();
    private final Screen returnTo;
    private int scroll;
    private String status = "";

    public RoleSettingsScreen() { this(new MafiaGamesScreen()); }
    public RoleSettingsScreen(Screen returnTo) { super(Component.literal("Настройки ролей")); this.returnTo = returnTo; }

    @Override protected void init() {
        List<RoleDefinition> roles = service.roleTemplates();
        int rowHeight = 38;
        int visible = Math.max(1, (height - 120) / rowHeight);
        scroll = Math.min(scroll, Math.max(0, roles.size() - visible));
        int left = Math.max(18, width / 2 - 250);
        int rowWidth = Math.min(500, width - left - 18);
        for (int i = scroll; i < roles.size() && i < scroll + visible; i++) {
            RoleDefinition role = roles.get(i);
            int y = 64 + (i - scroll) * rowHeight;
            int toggleW = 74, editW = 90, deleteW = 30;
            int nameW = Math.max(100, rowWidth - toggleW - editW - deleteW - 12);
            int color = role.color == null ? com.mlpgod.mafiatool.model.RoleColor.defaultFor(role.team).rgb : role.color.rgb;
            addRenderableWidget(Button.builder(Component.literal((role.enabled ? "[Вкл] " : "[Выкл] ") + role.name + " ×" + role.count)
                            .withStyle(style -> style.withColor(TextColor.fromRgb(color))),
                    b -> { service.setRoleTemplateEnabled(role, !role.enabled); b.setMessage(Component.literal((role.enabled ? "[Вкл] " : "[Выкл] ") + role.name + " ×" + role.count)
                            .withStyle(style -> style.withColor(TextColor.fromRgb(color)))); })
                    .bounds(left, y, nameW, 24).build());
            addRenderableWidget(Button.builder(Component.literal("Изменить"), b -> Minecraft.getInstance().setScreen(new RoleEditorScreen(role, this)))
                    .bounds(left + nameW + 4, y, editW, 24).build());
            addRenderableWidget(Button.builder(Component.literal("Удалить"), b -> confirmDelete(role))
                    .bounds(left + nameW + editW + 8, y, deleteW + 20, 24).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Добавить роль"), b -> {
            RoleDefinition role = service.addRole();
            Minecraft.getInstance().setScreen(new RoleEditorScreen(role, this));
        }).bounds(Math.max(18, width / 2 - 250), height - 42, 120, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> Minecraft.getInstance().setScreen(returnTo))
                .bounds(width - 92, height - 42, 74, 24).build());
        if (roles.size() > visible) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> { scroll = Math.max(0, scroll - 1); rebuildWidgets(); })
                    .bounds(width - 48, 62, 24, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> { scroll = Math.min(roles.size() - visible, scroll + 1); rebuildWidgets(); })
                    .bounds(width - 48, height - 66, 24, 20).build());
        }
    }

    private void confirmDelete(RoleDefinition role) {
        Minecraft.getInstance().setScreen(new ConfirmScreen(yes -> {
            if (yes) service.deleteRole(role);
            Minecraft.getInstance().setScreen(this);
        }, Component.literal("Удалить роль?"), Component.literal("Снимки роли в существующих партиях сохранятся."), Component.literal("Удалить"), Component.literal("Отмена")));
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xA0101010);
        g.drawCenteredString(font, "Шаблоны ролей", width / 2, 20, 0xFFFFFF);
        g.drawCenteredString(font, "Изменения шаблонов не меняют роли в уже созданных партиях", width / 2, 39, 0xA0A0A0);
        List<RoleDefinition> roles = service.roleTemplates();
        int left = Math.max(18, width / 2 - 250);
        for (int i = scroll; i < roles.size() && i < scroll + Math.max(1, (height - 120) / 38); i++) {
            RoleDefinition role = roles.get(i);
            int y = 64 + (i - scroll) * 38;
            String team = role.team == null ? "" : role.team.name();
            int color = role.color == null ? com.mlpgod.mafiatool.model.RoleColor.defaultFor(role.team).rgb : role.color.rgb;
            g.drawString(font, team + (role.description.isBlank() ? "" : " · " + role.description), left + 5, y + 26, color);
        }
        if (!status.isBlank()) g.drawString(font, status, left, height - 65, 0xFFFFFF);
        super.render(g, mouseX, mouseY, delta);
    }

    @Override public boolean mouseScrolled(double x, double y, double hx, double vy) {
        int visible = Math.max(1, (height - 120) / 38);
        List<RoleDefinition> roles = service.roleTemplates();
        if (roles.size() > visible) {
            scroll = Math.max(0, Math.min(roles.size() - visible, scroll - (int) Math.signum(vy)));
            rebuildWidgets(); return true;
        }
        return super.mouseScrolled(x, y, hx, vy);
    }

    @Override public boolean isPauseScreen() { return false; }
}
