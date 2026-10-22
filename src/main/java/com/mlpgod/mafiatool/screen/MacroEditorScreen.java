package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.ActionMacro;
import com.mlpgod.mafiatool.model.EventType;
import com.mlpgod.mafiatool.model.MacroEffect;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class MacroEditorScreen extends Screen {
    private static final EventType[] TRIGGERS = {
            EventType.ACTION, EventType.KILL_ATTEMPT, EventType.CHECK, EventType.TREATMENT,
            EventType.CUSTOM_ACTION, EventType.VOTE, EventType.VOTE_RESULT,
            EventType.PLAYER_DIED, EventType.PLAYER_EXCLUDED, EventType.PHASE_CHANGED
    };
    private static final MacroEffect[] EFFECTS = MacroEffect.values();
    private final ActionMacro macro;
    private final Screen returnTo;
    private int triggerIndex;
    private int effectIndex;
    private int triggerScroll;
    private EditBox name;
    private EditBox condition;
    private EditBox textTemplate;
    private String error = "";

    public MacroEditorScreen(ActionMacro macro, Screen returnTo) {
        super(Component.literal("Редактор макроса"));
        this.macro = macro;
        this.returnTo = returnTo;
        for (int i = 0; i < TRIGGERS.length; i++) if (TRIGGERS[i] == macro.trigger) triggerIndex = i;
        for (int i = 0; i < EFFECTS.length; i++) if (EFFECTS[i] == macro.effect) effectIndex = i;
    }

    @Override protected void init() {
        int left = 16;
        int available = Math.max(300, width - 32);
        int triggerWidth = available * 34 / 100;
        int effectWidth = available * 26 / 100;
        int gap = 8;
        int effectX = left + triggerWidth + gap;
        int fieldsX = effectX + effectWidth + gap;
        int fieldsWidth = Math.max(120, left + available - fieldsX);
        int visibleTriggers = visibleTriggers();
        triggerScroll = Math.max(0, Math.min(triggerScroll, TRIGGERS.length - visibleTriggers));
        List<Button> triggerButtons = new ArrayList<>();
        for (int row = 0; row < visibleTriggers; row++) {
            int option = triggerScroll + row;
            Button button = Button.builder(Component.literal(triggerOptionLabel(option, triggerWidth)), b -> {
                triggerIndex = option;
                for (int i = 0; i < triggerButtons.size(); i++)
                    triggerButtons.get(i).setMessage(Component.literal(triggerOptionLabel(triggerScroll + i, triggerWidth)));
            }).bounds(left, 74 + row * 24, triggerWidth, 22).build();
            addRenderableWidget(button);
            triggerButtons.add(button);
        }
        List<Button> effectButtons = new ArrayList<>();
        for (int i = 0; i < EFFECTS.length; i++) {
            int option = i;
            Button button = Button.builder(Component.literal(effectOptionLabel(option)), b -> {
                effectIndex = option;
                for (int j = 0; j < effectButtons.size(); j++)
                    effectButtons.get(j).setMessage(Component.literal(effectOptionLabel(j)));
            }).bounds(effectX, 74 + i * 24, effectWidth, 22).build();
            addRenderableWidget(button);
            effectButtons.add(button);
        }

        name = field(fieldsX, 74, fieldsWidth, "Название макроса", macro.name, 50);
        condition = field(fieldsX, 124, fieldsWidth, "Фраза условия (необязательно)", macro.condition, 100);
        textTemplate = field(fieldsX, 174, fieldsWidth, "Текст шаблона", macro.textTemplate, 240);
        addRenderableWidget(Button.builder(Component.literal("Макрос включён: " + (macro.enabled ? "да" : "нет")), b -> {
            macro.enabled = !macro.enabled;
            b.setMessage(Component.literal("Макрос включён: " + (macro.enabled ? "да" : "нет")));
        }).bounds(fieldsX, 204, fieldsWidth, 22).build());

        int bottom = height - 42;
        addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> save()).bounds(fieldsX, bottom, 110, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> Minecraft.getInstance().setScreen(returnTo))
                .bounds(fieldsX + 118, bottom, 90, 24).build());
    }

    private EditBox field(int x, int y, int w, String hint, String value, int max) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(max);
        box.setValue(value == null ? "" : value);
        addRenderableWidget(box);
        return box;
    }

    private int visibleTriggers() { return Math.max(4, Math.min(TRIGGERS.length, (height - 150) / 24)); }
    private EventType trigger() { return TRIGGERS[triggerIndex]; }
    private MacroEffect effect() { return EFFECTS[effectIndex]; }

    private String eventLabel(EventType event) {
        return switch (event) {
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
            default -> event.name();
        };
    }

    private String effectLabel(MacroEffect macroEffect) {
        return switch (macroEffect) {
            case ADD_LOG_NOTE -> "Добавить запись";
            case MARK_TARGET_DEAD -> "Отметить цель погибшей";
            case ADVANCE_PHASE -> "Переключить фазу";
        };
    }

    private String triggerOptionLabel(int option, int pixelWidth) {
        String prefix = triggerIndex == option ? "[x] " : "[ ] ";
        int chars = Math.max(8, pixelWidth / 6 - prefix.length());
        String label = eventLabel(TRIGGERS[option]);
        if (label.length() > chars) label = label.substring(0, chars - 1) + "…";
        return prefix + label;
    }

    private String effectOptionLabel(int option) {
        return (effectIndex == option ? "[x] " : "[ ] ") + effectLabel(EFFECTS[option]);
    }

    private void save() {
        try {
            macro.name = name.getValue();
            macro.trigger = trigger();
            macro.condition = condition.getValue();
            macro.effect = effect();
            macro.textTemplate = textTemplate.getValue();
            GameService.get().saveMacro(macro);
            Minecraft.getInstance().setScreen(returnTo);
        } catch (Exception e) {
            error = e.getMessage() == null ? "Макрос не сохранён" : e.getMessage();
        }
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int left = 16;
        int triggerWidth = Math.max(300, width - 32) * 34 / 100;
        int maxScroll = TRIGGERS.length - visibleTriggers();
        if (x >= left && x < left + triggerWidth && y >= 74 && y < 74 + visibleTriggers() * 24 && maxScroll > 0) {
            triggerScroll = Math.max(0, Math.min(maxScroll, triggerScroll - (int) Math.signum(vertical)));
            String nameValue = name == null ? "" : name.getValue();
            String conditionValue = condition == null ? "" : condition.getValue();
            String templateValue = textTemplate == null ? "" : textTemplate.getValue();
            rebuildWidgets();
            if (name != null) name.setValue(nameValue);
            if (condition != null) condition.setValue(conditionValue);
            if (textTemplate != null) textTemplate.setValue(templateValue);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public void render(GuiGraphics g, int x, int y, float delta) {
        g.fill(0, 0, width, height, 0xA0101010);
        int left = 16;
        int available = Math.max(300, width - 32);
        int triggerWidth = available * 34 / 100;
        int effectWidth = available * 26 / 100;
        int fieldsX = left + triggerWidth + 8 + effectWidth + 8;
        g.drawCenteredString(font, "Редактор макроса", width / 2, 20, 0xFFFFFF);
        g.drawString(font, "Запускать при", left, 54, 0xFFFFFF);
        g.drawString(font, "Эффект", left + triggerWidth + 8, 54, 0xFFFFFF);
        g.drawString(font, "Параметры", fieldsX, 54, 0xFFFFFF);
        if (TRIGGERS.length > visibleTriggers()) g.drawString(font, "Колёсико — прокрутка", left, 64, 0x909090);
        g.drawString(font, "Условие проверяет описание и результат события", fieldsX, 114, 0xA0A0A0);
        g.drawString(font, "Для смерти цели условие обязательно", fieldsX, 164, 0xFFCC88);
        g.drawString(font, "Подстановки в тексте:", fieldsX, 236, 0xC0C0C0);
        g.drawString(font, "{actor} {target} {phase}", fieldsX, 250, 0xA0A0A0);
        g.drawString(font, "{description} {result} {macro}", fieldsX, 262, 0xA0A0A0);
        if (effect() == MacroEffect.MARK_TARGET_DEAD)
            g.drawString(font, "Цель погибнет только при совпадении условия.", fieldsX, 282, 0xFFCC88);
        if (!error.isBlank()) g.drawCenteredString(font, error, width / 2, height - 58, 0xFFFF8888);
        super.render(g, x, y, delta);
    }

    @Override public boolean isPauseScreen() { return false; }
}
