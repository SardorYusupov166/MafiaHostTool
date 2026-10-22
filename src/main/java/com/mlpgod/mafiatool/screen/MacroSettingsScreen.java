package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.ActionMacro;
import com.mlpgod.mafiatool.model.EventType;
import com.mlpgod.mafiatool.model.MacroEffect;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class MacroSettingsScreen extends Screen {
    private final GameService service = GameService.get();
    private final Screen returnTo;
    private int scroll;

    public MacroSettingsScreen(Screen returnTo) {
        super(Component.literal("Пользовательские макросы"));
        this.returnTo = returnTo;
    }

    @Override protected void init() {
        List<ActionMacro> macros = service.actionMacros();
        int left = Math.max(16, width / 2 - 300);
        int rowWidth = Math.min(600, width - left - 16);
        int visible = Math.max(1, (height - 142) / 42);
        scroll = Math.min(scroll, Math.max(0, macros.size() - visible));
        for (int i = scroll; i < macros.size() && i < scroll + visible; i++) {
            ActionMacro macro = macros.get(i);
            int y = 68 + (i - scroll) * 42;
            int editWidth = 76, deleteWidth = 68;
            int toggleWidth = Math.max(100, rowWidth - editWidth - deleteWidth - 12);
            addRenderableWidget(Button.builder(Component.literal((macro.enabled ? "[Вкл] " : "[Выкл] ") + macro.name), b -> {
                service.setMacroEnabled(macro.id, !macro.enabled);
                rebuildWidgets();
            }).bounds(left, y, toggleWidth, 22).build());
            addRenderableWidget(Button.builder(Component.literal("Изменить"), b -> Minecraft.getInstance().setScreen(new MacroEditorScreen(macro.copy(), this)))
                    .bounds(left + toggleWidth + 4, y, editWidth, 22).build());
            addRenderableWidget(Button.builder(Component.literal("Удалить"), b -> confirmDelete(macro))
                    .bounds(left + toggleWidth + editWidth + 8, y, deleteWidth, 22).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Добавить макрос"), b ->
                        Minecraft.getInstance().setScreen(new MacroEditorScreen(new ActionMacro(), this)))
                .bounds(Math.max(16, width / 2 - 300), height - 42, 130, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> Minecraft.getInstance().setScreen(returnTo))
                .bounds(width - 92, height - 42, 74, 24).build());
        if (macros.size() > visible) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> { scroll = Math.max(0, scroll - 1); rebuildWidgets(); })
                    .bounds(width - 46, 66, 22, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> { scroll = Math.min(macros.size() - visible, scroll + 1); rebuildWidgets(); })
                    .bounds(width - 46, height - 66, 22, 20).build());
        }
    }

    private void confirmDelete(ActionMacro macro) {
        Minecraft.getInstance().setScreen(new ConfirmScreen(yes -> {
            if (yes) service.deleteMacro(macro.id);
            Minecraft.getInstance().setScreen(this);
        }, Component.literal("Удалить макрос?"), Component.literal("Макрос перестанет применяться ко всем новым событиям."), Component.literal("Удалить"), Component.literal("Отмена")));
    }

    private String eventLabel(EventType type) {
        return switch (type) {
            case ACTION -> "Действие";
            case KILL_ATTEMPT -> "Попытка убийства";
            case CHECK -> "Проверка";
            case TREATMENT -> "Лечение";
            case CUSTOM_ACTION -> "Произвольное действие";
            case VOTE -> "Голос";
            case VOTE_RESULT -> "Итог голосования";
            case PLAYER_DIED -> "Смерть игрока";
            case PLAYER_EXCLUDED -> "Исключение игрока";
            case PHASE_CHANGED -> "Смена фазы";
            default -> type.name();
        };
    }

    private String effectLabel(MacroEffect effect) {
        return switch (effect) {
            case ADD_LOG_NOTE -> "добавить запись";
            case MARK_TARGET_DEAD -> "отметить цель погибшей";
            case ADVANCE_PHASE -> "переключить фазу";
        };
    }

    private String clip(String value, int pixels) {
        int max = Math.max(10, pixels / 6);
        return value.length() > max ? value.substring(0, max - 1) + "…" : value;
    }

    @Override public void render(GuiGraphics g, int x, int y, float delta) {
        g.fill(0, 0, width, height, 0xA0101010);
        g.drawCenteredString(font, "Пользовательские макросы", width / 2, 20, 0xFFFFFF);
        g.drawCenteredString(font, "Правила применяются автоматически к новым событиям во всех партиях", width / 2, 40, 0xA0A0A0);
        List<ActionMacro> macros = service.actionMacros();
        int left = Math.max(16, width / 2 - 300);
        int rowWidth = Math.min(600, width - left - 16);
        int visible = Math.max(1, (height - 142) / 42);
        for (int i = scroll; i < macros.size() && i < scroll + visible; i++) {
            ActionMacro macro = macros.get(i);
            int yRow = 68 + (i - scroll) * 42;
            String detail = eventLabel(macro.trigger) + " → " + effectLabel(macro.effect)
                    + (macro.condition == null || macro.condition.isBlank() ? " · без условия" : " · если содержит: " + macro.condition);
            g.drawString(font, clip(detail, rowWidth - 8), left + 5, yRow + 25, macro.enabled ? 0xC0C0C0 : 0x707070);
        }
        if (macros.isEmpty()) g.drawCenteredString(font, "Макросов пока нет — добавьте правило для события", width / 2, height / 2, 0xC0C0C0);
        super.render(g, x, y, delta);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int visible = Math.max(1, (height - 142) / 42);
        List<ActionMacro> macros = service.actionMacros();
        if (macros.size() > visible) {
            scroll = Math.max(0, Math.min(macros.size() - visible, scroll - (int) Math.signum(vertical)));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public boolean isPauseScreen() { return false; }
}
