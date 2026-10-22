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
        int left = width / 2 - 190;
        int fieldWidth = 380;
        name = field(left, 78, fieldWidth, "Название макроса", macro.name, 50);
        addRenderableWidget(Button.builder(Component.literal("Событие: " + eventLabel()), b -> {
            triggerIndex = (triggerIndex + 1) % TRIGGERS.length;
            b.setMessage(Component.literal("Событие: " + eventLabel()));
        }).bounds(left, 108, fieldWidth, 22).build());
        condition = field(left, 138, fieldWidth, "Условие в описании или результате (необязательно)", macro.condition, 100);
        addRenderableWidget(Button.builder(Component.literal("Что сделать: " + effectLabel()), b -> {
            effectIndex = (effectIndex + 1) % EFFECTS.length;
            b.setMessage(Component.literal("Что сделать: " + effectLabel()));
        }).bounds(left, 168, fieldWidth, 22).build());
        textTemplate = field(left, 198, fieldWidth, "Текст шаблона", macro.textTemplate, 240);
        addRenderableWidget(Button.builder(Component.literal("Макрос включён: " + (macro.enabled ? "да" : "нет")), b -> {
            macro.enabled = !macro.enabled;
            b.setMessage(Component.literal("Макрос включён: " + (macro.enabled ? "да" : "нет")));
        }).bounds(left, 228, fieldWidth, 22).build());
        int bottom = height - 42;
        addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> save()).bounds(left, bottom, 110, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> Minecraft.getInstance().setScreen(returnTo))
                .bounds(left + 118, bottom, 90, 24).build());
    }

    private EditBox field(int x, int y, int w, String hint, String value, int max) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(max);
        box.setValue(value == null ? "" : value);
        addRenderableWidget(box);
        return box;
    }

    private EventType trigger() { return TRIGGERS[triggerIndex]; }
    private MacroEffect effect() { return EFFECTS[effectIndex]; }

    private String eventLabel() {
        return switch (trigger()) {
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
            default -> trigger().name();
        };
    }

    private String effectLabel() {
        return switch (effect()) {
            case ADD_LOG_NOTE -> "добавить запись в журнал";
            case MARK_TARGET_DEAD -> "отметить цель погибшей";
            case ADVANCE_PHASE -> "переключить день / ночь";
        };
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

    @Override public void render(GuiGraphics g, int x, int y, float delta) {
        g.fill(0, 0, width, height, 0xA0101010);
        g.drawCenteredString(font, "Редактор макроса", width / 2, 22, 0xFFFFFF);
        int left = width / 2 - 190;
        g.drawString(font, "Название", left, 66, 0xC0C0C0);
        g.drawString(font, "Если событие содержит эту фразу — макрос запускается", left, 128, 0xC0C0C0);
        g.drawString(font, "Текст для записи или причина смерти (для выбранного эффекта)", left, 188, 0xC0C0C0);
        g.drawCenteredString(font, "Подстановки: {actor} {target} {phase} {description} {result} {macro}", width / 2, 260, 0xA0A0A0);
        g.drawCenteredString(font, "Для смерти цели условие обязательно, чтобы не отметить спасённого игрока погибшим.", width / 2, 276, 0xFFCC88);
        if (!error.isBlank()) g.drawCenteredString(font, error, width / 2, height - 58, 0xFFFF8888);
        super.render(g, x, y, delta);
    }

    @Override public boolean isPauseScreen() { return false; }
}
