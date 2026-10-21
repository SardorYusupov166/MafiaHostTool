package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.RoleDefinition;
import com.mlpgod.mafiatool.model.RolePhase;
import com.mlpgod.mafiatool.model.RoleTeam;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class RoleEditorScreen extends Screen {
    private final RoleDefinition role;
    private final Screen returnTo;
    private EditBox name;
    private EditBox customTeam;
    private EditBox description;
    private EditBox count;
    private RoleTeam team;
    private RolePhase phase;
    private boolean enabled;
    private String status = "";

    public RoleEditorScreen(RoleDefinition role, Screen returnTo) {
        super(Component.literal("Редактор роли"));
        this.role = role;
        this.returnTo = returnTo;
        this.team = role.team == null ? RoleTeam.TOWN : role.team;
        this.phase = role.actionPhase == null ? RolePhase.ANY : role.actionPhase;
        this.enabled = role.enabled;
    }

    @Override protected void init() {
        int left = width / 2 - 170;
        int y = 66;
        name = edit(left + 100, y, 240, "Название роли", role.name);
        addRenderableWidget(Button.builder(Component.literal("Команда: " + teamLabel()), b -> {
            team = RoleTeam.values()[(team.ordinal() + 1) % RoleTeam.values().length];
            b.setMessage(Component.literal("Команда: " + teamLabel()));
        }).bounds(left, y + 34, 150, 22).build());
        customTeam = edit(left + 158, y + 34, 182, "Название своей команды", role.customTeam);
        addRenderableWidget(Button.builder(Component.literal("Фаза: " + phaseLabel()), b -> {
            phase = RolePhase.values()[(phase.ordinal() + 1) % RolePhase.values().length];
            b.setMessage(Component.literal("Фаза: " + phaseLabel()));
        }).bounds(left, y + 68, 150, 22).build());
        count = edit(left + 100, y + 102, 56, "Количество", Integer.toString(role.count));
        count.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        addRenderableWidget(Button.builder(Component.literal(enabled ? "Включена" : "Выключена"), b -> {
            enabled = !enabled; b.setMessage(Component.literal(enabled ? "Включена" : "Выключена"));
        }).bounds(left + 166, y + 102, 112, 22).build());
        int descriptionWidth = width - 40;
        int descriptionLeft = (width - descriptionWidth) / 2;
        description = edit(descriptionLeft, y + 150, descriptionWidth, "Описание роли", role.description);
        description.setMaxLength(400);
        addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> save())
                .bounds(left, height - 42, 104, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> Minecraft.getInstance().setScreen(returnTo))
                .bounds(left + 112, height - 42, 90, 24).build());
    }

    private EditBox edit(int x, int y, int w, String hint, String value) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(hint));
        box.setHint(Component.literal(hint)); box.setMaxLength(120); box.setValue(value == null ? "" : value);
        addRenderableWidget(box); return box;
    }

    private void save() {
        int number;
        try { number = Integer.parseInt(count.getValue()); }
        catch (Exception e) { status = "Укажите количество от 0 до 50"; return; }
        try {
            GameService.get().setRole(role, name.getValue(), team, customTeam.getValue(), description.getValue(), phase, number, enabled);
            Minecraft.getInstance().setScreen(returnTo);
        } catch (Exception e) { status = e.getMessage(); }
    }

    private String teamLabel() {
        return switch (team) { case TOWN -> "Мирные"; case MAFIA -> "Мафия"; case NEUTRAL -> "Нейтральные"; case CUSTOM -> "Своя"; };
    }
    private String phaseLabel() { return switch (phase) { case DAY -> "День"; case NIGHT -> "Ночь"; case ANY -> "Любая"; }; }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xA0101010);
        g.drawCenteredString(font, "Редактирование роли", width / 2, 20, 0xFFFFFF);
        int left = width / 2 - 170;
        g.drawString(font, "Название:", left, 71, 0xD0D0D0);
        g.drawString(font, "Количество:", left, 174, 0xD0D0D0);
        int previewWidth = width - 40;
        int previewLeft = 20;
        g.drawString(font, "Описание:", previewLeft, 204, 0xD0D0D0);
        String descriptionText = description == null ? role.description : description.getValue();
        if (descriptionText == null || descriptionText.isBlank()) {
            g.drawString(font, "Предпросмотр: описание пока пустое", previewLeft, 244, 0x909090);
        } else {
            g.drawString(font, "Предпросмотр:", previewLeft, 242, 0xA0A0A0);
            var lines = font.split(Component.literal(descriptionText), previewWidth);
            int previewBottom = height - 58;
            int maxLines = Math.max(0, (previewBottom - 258) / 10);
            for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
                g.drawString(font, lines.get(i), previewLeft, 254 + i * 10, 0xFFFFFF);
            }
            if (lines.size() > maxLines && maxLines > 0) {
                g.drawString(font, "…", previewLeft, 254 + (maxLines - 1) * 10, 0xFFFFFF);
            }
        }
        if (!status.isBlank()) g.drawString(font, status, left, height - 60, 0xFF8888);
        super.render(g, mouseX, mouseY, delta);
    }

    @Override public boolean isPauseScreen() { return false; }
}
