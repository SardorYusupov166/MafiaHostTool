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
    private String message = "";

    public MafiaGameScreen(GameRecord game) { super(Component.literal(game.title)); this.game = game; }

    @Override protected void init() {
        boolean editable = game.status != GameStatus.FINISHED;
        int left = 14;
        int panelW = Math.min(470, width / 2 + 20);
        int rows = Math.max(1, Math.min(12, (height - 182) / 26));
        playerScroll = Math.min(playerScroll, Math.max(0, game.players.size() - rows));
        for (int i = playerScroll; i < game.players.size() && i < playerScroll + rows; i++) {
            GamePlayer player = game.players.get(i);
            int y = 74 + (i - playerScroll) * 26;
            int nameW = Math.max(100, panelW - (editable ? 242 : 84));
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
            if (editable) {
                addRenderableWidget(Button.builder(Component.literal(statusLabel(player.status)), b -> Minecraft.getInstance().setScreen(new PlayerStatusScreen(game, player)))
                        .bounds(controlsX, y, 70, 22).build());
                addRenderableWidget(Button.builder(Component.literal("Роль"), b -> Minecraft.getInstance().setScreen(new RoleChangeScreen(game, player)))
                        .bounds(controlsX + 74, y, 44, 22).build());
                addRenderableWidget(Button.builder(Component.literal("Ник"), b -> Minecraft.getInstance().setScreen(new RenamePlayerScreen(game, player, this)))
                        .bounds(controlsX + 122, y, 44, 22).build());
            }
            addRenderableWidget(Button.builder(Component.literal("История"), b -> Minecraft.getInstance().setScreen(new EventLogScreen(game, player.id, this)))
                    .bounds(controlsX + (editable ? 170 : 0), y, 70, 22).build());
        }

        int rightX = Math.max(width / 2 + 14, left + panelW + 12);
        int actionY = height - 54;
        if (editable) {
            addRenderableWidget(Button.builder(Component.literal("+ Действие"), b -> Minecraft.getInstance().setScreen(new EventEntryScreen(game)))
                    .bounds(rightX, actionY, 98, 22).build());
            addRenderableWidget(Button.builder(Component.literal("Журнал"), b -> Minecraft.getInstance().setScreen(new EventLogScreen(game, null, this)))
                    .bounds(rightX + 104, actionY, 78, 22).build());
            if (game.phase.kind == PhaseKind.DAY) addRenderableWidget(Button.builder(Component.literal("Голосование"), b -> Minecraft.getInstance().setScreen(new VotingScreen(game)))
                    .bounds(rightX + 188, actionY, 100, 22).build());
            addRenderableWidget(Button.builder(Component.literal("Сменить фазу"), b -> advance())
                    .bounds(rightX, actionY - 30, 112, 22).build());
            addRenderableWidget(Button.builder(Component.literal("Исправить фазу"), b -> Minecraft.getInstance().setScreen(new PhaseCorrectionScreen(game, this)))
                    .bounds(rightX + 118, actionY - 30, 120, 22).build());
            addRenderableWidget(Button.builder(Component.literal("Завершить"), b -> Minecraft.getInstance().setScreen(new FinishGameScreen(game)))
                    .bounds(rightX + 244, actionY - 30, 88, 22).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Журнал партии"), b -> Minecraft.getInstance().setScreen(new EventLogScreen(game, null, this)))
                    .bounds(rightX, actionY, 132, 22).build());
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

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xA0101010);
        g.drawCenteredString(font, game.title + (game.status == GameStatus.FINISHED ? " · архив" : " · " + game.phase), width / 2, 16, 0xFFFFFF);
        g.drawCenteredString(font, "Живы " + game.aliveCount() + " / " + game.players.size(), width / 2, 34, 0xA0A0A0);
        int left = 14;
        int panelW = Math.min(470, width / 2 + 20);
        g.drawString(font, "Участник · роль · статус", left, 55, 0xFFFFFF);
        int rightX = Math.max(width / 2 + 14, left + panelW + 12);
        g.drawString(font, "Последние события", rightX, 55, 0xFFFFFF);
        int start = Math.max(0, game.events.size() - 6);
        for (int i = start; i < game.events.size(); i++) {
            GameEvent event = game.events.get(i);
            int y = 75 + (i - start) * 25;
            String text = TIME.format(Instant.ofEpochMilli(event.timestamp)) + " · " + event.phase + " · " + event.actorLabel + " " + event.description;
            g.drawString(font, clip(text, width - rightX - 20), rightX, y, 0xD0D0D0);
            if (!event.result.isBlank()) g.drawString(font, clip("Результат: " + event.result, width - rightX - 20), rightX, y + 11, 0x909090);
        }
        if (!message.isBlank()) g.drawString(font, message, rightX, height - 78, 0xFFFFFF);
        super.render(g, mouseX, mouseY, delta);
    }

    private String clip(String value, int width) {
        int max = Math.max(8, width / 6);
        return value.length() > max ? value.substring(0, max - 1) + "…" : value;
    }
    @Override public boolean isPauseScreen() { return false; }
}
