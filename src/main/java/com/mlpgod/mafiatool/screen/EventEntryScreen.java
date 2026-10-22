package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.*;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Action attempt and outcome are separate fields. No action here changes a player's status. */
public final class EventEntryScreen extends Screen {
    private final GameRecord game;
    private final List<GamePlayer> active;
    private final Screen returnTo;
    private int actorIndex = -1;
    private int targetIndex = -1;
    private int typeIndex;
    private EditBox description;
    private EditBox result;
    private String error = "";

    public EventEntryScreen(GameRecord game) {
        super(Component.literal("Записать действие"));
        this.game = game;
        this.returnTo = new MafiaGameScreen(game);
        this.active = game.players.stream().filter(p -> p.status == PlayerStatus.ALIVE).toList();
    }

    @Override protected void init() {
        int left = width / 2 - 170;
        addRenderableWidget(Button.builder(Component.literal("Кто выполнил действие: " + actorLabel()), b -> {
            if (!active.isEmpty()) actorIndex = actorIndex >= active.size() - 1 ? 0 : actorIndex + 1;
            b.setMessage(Component.literal("Кто выполнил действие: " + actorLabel()));
        }).bounds(left, 72, 340, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Цель: " + targetLabel()), b -> {
            targetIndex = targetIndex >= active.size() - 1 ? -1 : targetIndex + 1;
            b.setMessage(Component.literal("Цель: " + targetLabel()));
        }).bounds(left, 100, 340, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Тип: " + typeLabel()), b -> {
            typeIndex = (typeIndex + 1) % actionTypes().length;
            b.setMessage(Component.literal("Тип: " + typeLabel()));
        }).bounds(left, 128, 340, 22).build());
        description = edit(left, 163, 340, "Описание действия", 200);
        result = edit(left, 191, 340, "Результат: успешно / сорвано / спасён", 200);
        addRenderableWidget(Button.builder(Component.literal("Записать в журнал"), b -> save()).bounds(left, 226, 160, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> Minecraft.getInstance().setScreen(returnTo)).bounds(left + 168, 226, 86, 22).build());
    }

    private EditBox edit(int x, int y, int w, String hint, int max) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(hint)); box.setHint(Component.literal(hint)); box.setMaxLength(max); addRenderableWidget(box); return box;
    }
    private EventType[] actionTypes() { return new EventType[]{EventType.ACTION, EventType.KILL_ATTEMPT, EventType.CHECK, EventType.TREATMENT, EventType.CUSTOM_ACTION}; }
    private String actorLabel() { return actorIndex < 0 || active.isEmpty() ? "выберите игрока" : active.get(actorIndex).name; }
    private String targetLabel() { return targetIndex < 0 ? "не выбрана" : active.get(targetIndex).name; }
    private String typeLabel() {
        return switch (actionTypes()[typeIndex]) {
            case ACTION -> "Произвольное действие";
            case KILL_ATTEMPT -> "Попытка убийства";
            case CHECK -> "Проверка";
            case TREATMENT -> "Лечение";
            default -> "Другое действие";
        };
    }

    private void save() {
        try {
            if (actorIndex < 0 || actorIndex >= active.size()) throw new IllegalStateException("Выберите игрока, который выполнил действие");
            String actorId = active.get(actorIndex).id;
            String targetId = targetIndex < 0 ? null : active.get(targetIndex).id;
            String text = description.getValue().isBlank() ? typeLabel() : description.getValue();
            GameService.get().recordAction(game, actionTypes()[typeIndex], actorId, targetId, text, result.getValue());
            Minecraft.getInstance().setScreen(returnTo);
        } catch (Exception e) { error = e.getMessage(); }
    }

    @Override public void render(GuiGraphics g, int x, int y, float d) {
        g.fill(0, 0, width, height, 0xA0101010);
        g.drawCenteredString(font, "Действие · " + game.phase, width / 2, 24, 0xFFFFFF);
        g.drawCenteredString(font, "Запись действия не меняет автоматически статус цели", width / 2, 45, 0xA0A0A0);
        if (!error.isBlank()) g.drawCenteredString(font, error, width / 2, 264, 0xFF8888);
        super.render(g, x, y, d);
    }
    @Override public boolean isPauseScreen() { return false; }
}
