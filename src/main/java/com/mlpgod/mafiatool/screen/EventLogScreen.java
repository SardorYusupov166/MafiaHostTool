package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.*;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class EventLogScreen extends Screen {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss").withZone(ZoneId.systemDefault());
    private final GameRecord game;
    private final Screen returnTo;
    private PhaseKind phase;
    private String playerId;
    private EventType type;
    private int scroll;

    public EventLogScreen(GameRecord game, String playerId, Screen returnTo) {
        super(Component.literal("Журнал партии")); this.game = game; this.playerId = playerId; this.returnTo = returnTo;
    }

    @Override protected void init() {
        int left = 14;
        addRenderableWidget(Button.builder(Component.literal("Фаза: " + phaseLabel()), b -> {
            PhaseKind[] phases = {null, PhaseKind.NIGHT, PhaseKind.DAY, PhaseKind.PREPARATION, PhaseKind.FINISHED};
            int next = phase == null ? 1 : (java.util.Arrays.asList(phases).indexOf(phase) + 1) % phases.length;
            phase = phases[next]; b.setMessage(Component.literal("Фаза: " + phaseLabel())); scroll = 0; rebuildWidgets();
        }).bounds(left, 48, 122, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Игрок: " + playerLabel()), b -> {
            List<GamePlayer> players = game.players;
            int index = playerId == null ? -1 : java.util.stream.IntStream.range(0, players.size()).filter(i -> players.get(i).id.equals(playerId)).findFirst().orElse(-1);
            int next = index + 1;
            playerId = next >= players.size() ? null : players.get(next).id;
            b.setMessage(Component.literal("Игрок: " + playerLabel())); scroll = 0; rebuildWidgets();
        }).bounds(left + 128, 48, 142, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Тип: " + typeLabel()), b -> {
            EventType[] all = EventType.values();
            type = type == null ? all[0] : type.ordinal() == all.length - 1 ? null : all[type.ordinal() + 1];
            b.setMessage(Component.literal("Тип: " + typeLabel())); scroll = 0; rebuildWidgets();
        }).bounds(left + 276, 48, 160, 22).build());
        int visible = Math.max(1, (height - 140) / 34);
        List<GameEvent> events = filtered();
        scroll = Math.min(scroll, Math.max(0, events.size() - visible));
        for (int i = scroll; i < events.size() && i < scroll + visible; i++) {
            GameEvent event = events.get(i); int y = 82 + (i - scroll) * 34;
            if (game.status != GameStatus.FINISHED) {
                addRenderableWidget(Button.builder(Component.literal("Исправить"), b -> Minecraft.getInstance().setScreen(new EventCorrectionScreen(game, event, this)))
                        .bounds(width - 88, y, 74, 24).build());
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> Minecraft.getInstance().setScreen(returnTo))
                .bounds(width - 88, height - 32, 70, 22).build());
        if (events.size() > visible) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> { scroll = Math.max(0, scroll - 1); rebuildWidgets(); }).bounds(width - 42, 78, 22, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> { scroll = Math.min(events.size() - visible, scroll + 1); rebuildWidgets(); }).bounds(width - 42, height - 60, 22, 20).build());
        }
    }

    private List<GameEvent> filtered() { return GameService.get().filterEvents(game, phase, playerId, type); }
    private String phaseLabel() { return phase == null ? "Все" : switch (phase) { case PREPARATION -> "Подготовка"; case NIGHT -> "Ночь"; case DAY -> "День"; case FINISHED -> "Завершение"; }; }
    private String playerLabel() { return playerId == null ? "Все" : game.players.stream().filter(p -> p.id.equals(playerId)).map(p -> p.name).findFirst().orElse("Все"); }
    private String typeLabel() { return typeLabelFor(type); }
    private String typeLabelFor(EventType eventType) {
        if (eventType == null) return "Все";
        return switch (eventType) {
        case GAME_CREATED -> "Создание партии";
        case GAME_STARTED -> "Начало партии";
        case ROLES_ASSIGNED -> "Распределение ролей";
        case ROLES_REDISTRIBUTED -> "Перераспределение ролей";
        case PHASE_CHANGED -> "Смена фазы";
        case ACTION -> "Действие";
        case KILL_ATTEMPT -> "Попытка убийства";
        case CHECK -> "Проверка";
        case TREATMENT -> "Лечение";
        case CUSTOM_ACTION -> "Произвольное действие";
        case VOTE -> "Голос";
        case VOTE_RESULT -> "Итог голосования";
        case PLAYER_DIED -> "Смерть";
        case PLAYER_REVIVED -> "Возвращение в игру";
        case PLAYER_EXCLUDED -> "Исключение";
        case PLAYER_RESTORED -> "Восстановление игрока";
        case PLAYER_RENAMED -> "Изменение ника";
        case ROLE_CHANGED -> "Изменение роли";
        case GAME_FINISHED -> "Завершение партии";
        case GAME_REOPENED -> "Восстановление партии";
        case CORRECTION -> "Исправление";
    };
    }

    @Override public void render(GuiGraphics g, int x, int y, float d) {
        g.fill(0, 0, width, height, 0xA0101010);
        g.drawCenteredString(font, "Журнал · " + game.title, width / 2, 18, 0xFFFFFF);
        List<GameEvent> events = filtered();
        if (events.isEmpty()) g.drawCenteredString(font, "Нет событий по выбранным фильтрам", width / 2, height / 2, 0xA0A0A0);
        for (int i = scroll; i < events.size() && i < scroll + Math.max(1, (height - 140) / 34); i++) {
            GameEvent e = events.get(i); int row = i - scroll; int yy = 86 + row * 34;
            String first = TIME.format(Instant.ofEpochMilli(e.timestamp)) + " · " + e.phase + " · " + typeLabelFor(e.type) + " · " + e.actorLabel
                    + (e.targetLabel == null || e.targetLabel.isBlank() ? "" : " → " + e.targetLabel) + " · " + e.description;
            g.drawString(font, clip(first, width - 130), 16, yy, 0xE0E0E0);
            String second = e.result == null || e.result.isBlank() ? "" : "Результат: " + e.result;
            if (e.originalEventId != null) second += " · Исправление исходной записи";
            if (e.correctionNote != null && !e.correctionNote.isBlank()) second += " · Причина: " + e.correctionNote;
            if (!second.isBlank()) g.drawString(font, clip(second, width - 130), 28, yy + 12, 0xA0A0A0);
        }
        super.render(g, x, y, d);
    }

    private String clip(String s, int pixels) { int max = Math.max(12, pixels / 6); return s.length() > max ? s.substring(0, max - 1) + "…" : s; }
    @Override public boolean isPauseScreen() { return false; }
}
