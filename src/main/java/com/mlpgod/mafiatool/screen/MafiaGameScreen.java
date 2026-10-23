package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.*;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Moderator dashboard. It reads game state and delegates all changes to GameService. */
public final class MafiaGameScreen extends Screen {
    private static final GameService SERVICE = GameService.get();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private final GameRecord game;
    private int playerScroll;
    private int eventScroll = Integer.MAX_VALUE;
    private String message = "";

    public MafiaGameScreen(GameRecord game) { super(Component.literal(game.title)); this.game = game; }

    @Override protected void init() {
        boolean editable = game.status != GameStatus.FINISHED;
        int left = 14;
        int panelW = playerPanelWidth();
        int rows = Math.max(1, Math.min(12, (height - 182) / 26));
        playerScroll = Math.min(playerScroll, Math.max(0, game.players.size() - rows));
        for (int i = playerScroll; i < game.players.size() && i < playerScroll + rows; i++) {
            GamePlayer player = game.players.get(i);
            int y = 74 + (i - playerScroll) * 26;
            int controlsWidth = editable ? 214 : 54;
            int nameW = Math.max(1, panelW - 12 - controlsWidth);
            ChatFormatting nameColor = switch (player.status) {
                case ALIVE -> ChatFormatting.GREEN;
                case DEAD -> ChatFormatting.DARK_GRAY;
                case EXCLUDED -> ChatFormatting.RED;
            };
            String roleName = player.role == null ? "Роль не назначена" : player.role.name;
            RoleColor roleColor = player.role == null || player.role.color == null
                    ? RoleColor.defaultFor(player.role == null ? RoleTeam.TOWN : player.role.team) : player.role.color;
            String shownRole = clip(roleName, nameW / 2);
            int playerNameBudget = Math.max(1, nameW / 6 - shownRole.length() - 3);
            Component rowLabel = Component.literal(shownRole)
                    .withStyle(style -> style.withColor(TextColor.fromRgb(roleColor.rgb)))
                    .append(Component.literal(" · " + clip(player.name, playerNameBudget * 6)).withStyle(nameColor));
            addRenderableWidget(Button.builder(rowLabel, b -> {})
                    .bounds(left, y, nameW, 22).build());
            int controlsX = left + nameW + 4;
            addRenderableWidget(Button.builder(Component.literal("Лог"), b ->
                            Minecraft.getInstance().setScreen(new EventLogScreen(game, player.id, this)))
                    .bounds(editable ? controlsX + 170 : controlsX, y, editable ? 44 : 54, 22).build());
            if (editable) {
                addRenderableWidget(Button.builder(Component.literal(statusLabel(player.status)), b -> Minecraft.getInstance().setScreen(new PlayerStatusScreen(game, player)))
                        .bounds(controlsX, y, 70, 22).build());
                addRenderableWidget(Button.builder(Component.literal("Роль"), b -> Minecraft.getInstance().setScreen(new RoleChangeScreen(game, player)))
                        .bounds(controlsX + 74, y, 44, 22).build());
                addRenderableWidget(Button.builder(Component.literal("Ник"), b -> Minecraft.getInstance().setScreen(new RenamePlayerScreen(game, player, this)))
                        .bounds(controlsX + 122, y, 44, 22).build());
            }
        }

        int rightX = logPanelX();
        addRenderableWidget(Button.builder(Component.literal("Полный журнал"), b ->
                        Minecraft.getInstance().setScreen(new EventLogScreen(game, null, this)))
                .bounds(rightX, 48, 110, 22).build());
        int actionY = height - 54;
        int visibleEvents = visibleEvents();
        eventScroll = Math.min(eventScroll, Math.max(0, game.events.size() - visibleEvents));
        int firstVisibleEvent = Math.max(0, Math.min(eventScroll, Math.max(0, game.events.size() - visibleEvents)));
        for (int i = firstVisibleEvent; i < game.events.size() && i < firstVisibleEvent + visibleEvents; i++) {
            GameEvent event = game.events.get(i);
            int eventY = 76 + (i - eventScroll) * 32;
            if (editable) {
                addRenderableWidget(Button.builder(Component.literal("Исправить"), b ->
                                Minecraft.getInstance().setScreen(new EventCorrectionScreen(game, event, this)))
                        .bounds(width - 86, eventY, 72, 28).build());
            }
        }
        if (game.events.size() > visibleEvents) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
                eventScroll = Math.max(0, eventScroll - 1); rebuildWidgets();
            }).bounds(width - 44, 48, 22, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
                eventScroll = Math.min(game.events.size() - visibleEvents, eventScroll + 1); rebuildWidgets();
            }).bounds(width - 44, height - 78, 22, 20).build());
        }
        if (editable) {
            addRenderableWidget(Button.builder(Component.literal("+ Действие"), b -> Minecraft.getInstance().setScreen(new EventEntryScreen(game)))
                    .bounds(rightX, actionY, 98, 22).build());
            if (game.phase.kind == PhaseKind.DAY) addRenderableWidget(Button.builder(Component.literal("Голосование"), b -> Minecraft.getInstance().setScreen(new VotingScreen(game)))
                    .bounds(rightX + 104, actionY, 100, 22).build());
            addRenderableWidget(Button.builder(Component.literal("Сменить фазу"), b -> advance())
                    .bounds(rightX, actionY - 30, 112, 22).build());
            addRenderableWidget(Button.builder(Component.literal("Исправить фазу"), b -> Minecraft.getInstance().setScreen(new PhaseCorrectionScreen(game, this)))
                    .bounds(rightX + 118, actionY - 30, 120, 22).build());
            addRenderableWidget(Button.builder(Component.literal("Завершить"), b -> Minecraft.getInstance().setScreen(new FinishGameScreen(game)))
                    .bounds(rightX + 244, actionY - 30, 88, 22).build());
        }
        addRenderableWidget(Button.builder(Component.literal("К списку партий"), b -> Minecraft.getInstance().setScreen(new MafiaGamesScreen()))
                .bounds(width - 146, height - 30, 128, 22).build());
        if (game.players.size() > rows) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> { playerScroll = Math.max(0, playerScroll - 1); rebuildWidgets(); })
                    .bounds(left + panelW - 24, 50, 22, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> { playerScroll = Math.min(game.players.size() - rows, playerScroll + 1); rebuildWidgets(); })
                    .bounds(left + panelW - 24, 74 + rows * 26, 22, 20).build());
        }
    }

    private void advance() {
        try { SERVICE.advancePhase(game); message = "Фаза изменена"; rebuildWidgets(); }
        catch (Exception e) { message = e.getMessage(); }
    }

    private String statusLabel(PlayerStatus status) {
        return switch (status) { case ALIVE -> "Жив"; case DEAD -> "Мёртв"; case EXCLUDED -> "Исключён"; };
    }

    private int visibleEvents() {
        return Math.max(1, (height - 162) / 32);
    }

    private int playerPanelWidth() { return Math.min(470, width / 2 + 20); }

    private int logPanelX() { return Math.max(width / 2 + 14, 14 + playerPanelWidth() + 16); }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int rightX = logPanelX();
        if (mouseX >= rightX && game.events.size() > visibleEvents()) {
            int maximum = Math.max(0, game.events.size() - visibleEvents());
            eventScroll = Math.max(0, Math.min(maximum, eventScroll - (int) Math.signum(verticalAmount)));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xA0101010);
        int left = 14;
        int rightX = logPanelX();
        int panelBottom = Math.max(90, height - 82);
        // Give the action log a dedicated, readable surface over the world background.
        g.fill(rightX - 8, 44, width - 14, panelBottom, 0xC0181818);
        g.drawCenteredString(font, game.title + (game.status == GameStatus.FINISHED ? " · архив" : " · " + game.phase), width / 2, 16, 0xFFFFFF);
        g.drawCenteredString(font, "Живы " + game.aliveCount() + " / " + game.players.size(), width / 2, 34, 0xA0A0A0);
        g.drawString(font, "Участник · роль · статус", left, 55, 0xFFFFFF);
        // Draw widgets first; the log is painted afterwards so other screen layers cannot hide it.
        super.render(g, mouseX, mouseY, delta);
        g.drawString(font, "История действий · " + game.events.size(), rightX, 55, 0xFFFFFFFF);
        int visibleEvents = visibleEvents();
        int start = Math.max(0, Math.min(eventScroll, Math.max(0, game.events.size() - visibleEvents)));
        int rightPadding = game.status == GameStatus.FINISHED
                ? (game.events.size() > visibleEvents ? 52 : 24) : 104;
        int eventTextWidth = Math.max(40, width - rightX - rightPadding);
        if (game.events.isEmpty()) g.drawString(font, "Событий пока нет", rightX, 80, 0xFFC0C0C0);
        for (int i = start; i < game.events.size() && i < start + visibleEvents; i++) {
            GameEvent event = game.events.get(i);
            int y = 77 + (i - start) * 32;
            String target = event.targetLabel == null || event.targetLabel.isBlank() ? "" : " → " + event.targetLabel;
            String first = TIME.format(Instant.ofEpochMilli(event.timestamp)) + " · " + event.phase + " · " + event.actorLabel + target;
            String second = event.description == null ? "" : event.description;
            if (event.result != null && !event.result.isBlank()) second += " · " + event.result;
            g.drawString(font, clip(first, eventTextWidth), rightX, y, 0xFFFFFFFF);
            if (!second.isBlank()) g.drawString(font, clip(second, eventTextWidth), rightX, y + 12, 0xFFE0E0E0);
        }
        if (!message.isBlank()) g.drawString(font, message, rightX, height - 78, 0xFFFFFFFF);
    }

    private String clip(String value, int width) {
        int max = Math.max(8, width / 6);
        return value.length() > max ? value.substring(0, max - 1) + "…" : value;
    }
    @Override public boolean isPauseScreen() { return false; }
}
