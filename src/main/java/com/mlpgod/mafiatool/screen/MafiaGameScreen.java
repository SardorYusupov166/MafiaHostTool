package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.*;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Compact, non-pausing moderator window that leaves the in-game chat visible. */
public final class MafiaGameScreen extends Screen {
    private static final GameService SERVICE = GameService.get();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private final GameRecord game;
    private boolean playersTab;
    private int playerScroll;
    private int eventScroll = Integer.MAX_VALUE;
    private String message = "";

    public MafiaGameScreen(GameRecord game) { super(Component.literal(game.title)); this.game = game; }

    private int panelWidth() { return Math.min(520, width - 32); }
    private int panelHeight() { return Math.min(700, height - 32); }
    private int panelX() { return width - panelWidth() - 16; }
    private int panelY() { return 16; }
    private int contentTop() { return panelY() + 96; }
    private int footerTop() { return panelY() + panelHeight() - 68; }
    private int contentBottom() { return footerTop() - 8; }
    private int visiblePlayers() { return Math.max(1, (contentBottom() - contentTop()) / 26); }
    private int visibleEvents() { return Math.max(1, (contentBottom() - contentTop()) / 34); }

    @Override protected void init() {
        int x = panelX();
        int y = panelY();
        int w = panelWidth();
        boolean editable = game.status != GameStatus.FINISHED;

        addRenderableWidget(Button.builder(Component.literal("×"), b -> Minecraft.getInstance().setScreen(null))
                .bounds(x + w - 32, y + 10, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ИИ"), b ->
                        Minecraft.getInstance().setScreen(new AiAssistantScreen(game, this)))
                .bounds(x + w - 78, y + 10, 38, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Игроки"), b -> { playersTab = true; playerScroll = 0; rebuildWidgets(); })
                .bounds(x + 12, y + 48, 92, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Журнал"), b -> { playersTab = false; eventScroll = Integer.MAX_VALUE; rebuildWidgets(); })
                .bounds(x + 110, y + 48, 92, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Написать в чат"), b -> openChat())
                .bounds(x + 210, y + 48, 112, 22).build());
        if (!playersTab) {
            addRenderableWidget(Button.builder(Component.literal("Полный журнал"), b ->
                            Minecraft.getInstance().setScreen(new EventLogScreen(game, null, this)))
                    .bounds(x + w - 126, y + 48, 114, 22).build());
        }

        if (playersTab) {
            int rows = visiblePlayers();
            playerScroll = Math.min(playerScroll, Math.max(0, game.players.size() - rows));
            int nameWidth = Math.max(90, w - 32 - 218);
            for (int i = playerScroll; i < game.players.size() && i < playerScroll + rows; i++) {
                GamePlayer player = game.players.get(i);
                int rowY = contentTop() + (i - playerScroll) * 26;
                ChatFormatting nameColor = switch (player.status) {
                    case ALIVE -> ChatFormatting.GREEN;
                    case DEAD -> ChatFormatting.DARK_GRAY;
                    case EXCLUDED -> ChatFormatting.RED;
                };
                String roleName = player.role == null ? "Роль не назначена" : player.role.name;
                RoleColor roleColor = player.role == null || player.role.color == null
                        ? RoleColor.defaultFor(player.role == null ? RoleTeam.TOWN : player.role.team) : player.role.color;
                int nameBudget = Math.max(1, nameWidth / 6 - roleName.length() / 2 - 3);
                Component rowLabel = Component.literal(clip(roleName, nameWidth / 2))
                        .withStyle(style -> style.withColor(TextColor.fromRgb(roleColor.rgb)))
                        .append(Component.literal(" · " + clip(player.name, nameBudget * 6)).withStyle(nameColor));
                addRenderableWidget(Button.builder(rowLabel, b -> {})
                        .bounds(x + 12, rowY, nameWidth, 22).build());
                int controlsX = x + 12 + nameWidth + 4;
                addRenderableWidget(Button.builder(Component.literal("Лог"), b ->
                                Minecraft.getInstance().setScreen(new EventLogScreen(game, player.id, this)))
                        .bounds(controlsX + (editable ? 170 : 0), rowY, editable ? 44 : 54, 22).build());
                if (editable) {
                    addRenderableWidget(Button.builder(Component.literal(statusLabel(player.status)), b ->
                                    Minecraft.getInstance().setScreen(new PlayerStatusScreen(game, player)))
                            .bounds(controlsX, rowY, 70, 22).build());
                    addRenderableWidget(Button.builder(Component.literal("Роль"), b ->
                                    Minecraft.getInstance().setScreen(new RoleChangeScreen(game, player)))
                            .bounds(controlsX + 74, rowY, 44, 22).build());
                    addRenderableWidget(Button.builder(Component.literal("Ник"), b ->
                                    Minecraft.getInstance().setScreen(new RenamePlayerScreen(game, player, this)))
                            .bounds(controlsX + 122, rowY, 44, 22).build());
                }
            }
            if (game.players.isEmpty()) addRenderableWidget(Button.builder(Component.literal("Список участников пуст"), b -> {})
                    .bounds(x + 12, contentTop(), w - 24, 22).build());
        } else {
            int rows = visibleEvents();
            eventScroll = Math.min(eventScroll, Math.max(0, game.events.size() - rows));
            int start = Math.max(0, Math.min(eventScroll, Math.max(0, game.events.size() - rows)));
            for (int i = start; i < game.events.size() && i < start + rows; i++) {
                GameEvent event = game.events.get(i);
                int rowY = contentTop() + (i - start) * 34;
                if (editable) addRenderableWidget(Button.builder(Component.literal("Исправить"), b ->
                                Minecraft.getInstance().setScreen(new EventCorrectionScreen(game, event, this)))
                        .bounds(x + w - 84, rowY + 2, 72, 24).build());
            }
        }

        if (editable) {
            int footer = footerTop();
            addRenderableWidget(Button.builder(Component.literal("+ Действие"), b ->
                            Minecraft.getInstance().setScreen(new EventEntryScreen(game)))
                    .bounds(x + 12, footer, 96, 22).build());
            if (game.phase.kind == PhaseKind.DAY) addRenderableWidget(Button.builder(Component.literal("Голосование"), b ->
                            Minecraft.getInstance().setScreen(new VotingScreen(game)))
                    .bounds(x + 112, footer, 92, 22).build());
            addRenderableWidget(Button.builder(Component.literal("День / ночь"), b -> advance())
                    .bounds(x + 208, footer, 94, 22).build());
            addRenderableWidget(Button.builder(Component.literal("Правка фазы"), b ->
                            Minecraft.getInstance().setScreen(new PhaseCorrectionScreen(game, this)))
                    .bounds(x + 306, footer, 94, 22).build());
            addRenderableWidget(Button.builder(Component.literal("Завершить"), b ->
                            Minecraft.getInstance().setScreen(new FinishGameScreen(game)))
                    .bounds(x + 404, footer, 100, 22).build());
        }
        addRenderableWidget(Button.builder(Component.literal("К списку партий"), b ->
                        Minecraft.getInstance().setScreen(new MafiaGamesScreen()))
                .bounds(x + 12, panelY() + panelHeight() - 34, 132, 22).build());
    }

    private void openChat() {
        Screen backTo = this;
        Minecraft.getInstance().setScreen(new ReturnToGameChatScreen(backTo));
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_T) {
            openChat();
            return true;
        }
        return super.keyPressed(event);
    }

    private void advance() {
        try { SERVICE.advancePhase(game); message = "Фаза изменена"; rebuildWidgets(); }
        catch (Exception e) { message = e.getMessage(); }
    }

    private String statusLabel(PlayerStatus status) {
        return switch (status) { case ALIVE -> "Жив"; case DEAD -> "Мёртв"; case EXCLUDED -> "Исключён"; };
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= panelX() && mouseX <= panelX() + panelWidth() && mouseY >= contentTop() && mouseY < contentBottom()) {
            if (playersTab && game.players.size() > visiblePlayers()) {
                playerScroll = Math.max(0, Math.min(game.players.size() - visiblePlayers(), playerScroll - (int) Math.signum(verticalAmount)));
                rebuildWidgets();
                return true;
            }
            if (!playersTab && game.events.size() > visibleEvents()) {
                eventScroll = Math.max(0, Math.min(game.events.size() - visibleEvents(), eventScroll - (int) Math.signum(verticalAmount)));
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = panelX();
        int y = panelY();
        int w = panelWidth();
        int h = panelHeight();
        // Leave the rest of the game view and the chat HUD unobstructed.
        g.fill(x, y, x + w, y + h, 0xE010141C);
        g.fill(x, y, x + w, y + 2, 0xFF586D85);
        g.fill(x, y + 36, x + w, y + 38, 0xFF303C4B);
        g.fill(x + 8, y + 42, x + w - 8, y + 72, 0xC0181E28);
        g.fill(x + 8, footerTop() - 6, x + w - 8, y + h - 8, 0xC0181E28);

        String title = game.title + (game.status == GameStatus.FINISHED ? " · архив" : "");
        g.drawString(font, clip(title, w - 118), x + 12, y + 16, 0xFFFFFFFF);
        g.drawString(font, game.status == GameStatus.FINISHED ? "Завершена" : game.phase.toString(), x + 12, y + 28, 0xFFB5C4D6);
        g.drawString(font, "Живы " + game.aliveCount() + " / " + game.players.size(), x + 142, y + 28, 0xFF9CCC8A);
        if (playersTab) {
            int rows = visiblePlayers();
            if (game.players.size() > rows) g.drawString(font, "Колёсико — прокрутка участников", x + 12, contentTop() - 13, 0xFF9AA7B7);
            if (game.players.isEmpty()) g.drawCenteredString(font, "Добавьте игроков в настройках партии", x + w / 2, contentTop() + 30, 0xFFB0B8C4);
        } else {
            int rows = visibleEvents();
            g.drawString(font, "История действий · " + game.events.size(), x + 12, contentTop() - 13, 0xFFFFFFFF);
            if (game.events.size() > rows) g.drawString(font, "Колёсико — прокрутка журнала", x + 190, contentTop() - 13, 0xFF9AA7B7);
            int start = Math.max(0, Math.min(eventScroll, Math.max(0, game.events.size() - rows)));
            if (game.events.isEmpty()) g.drawCenteredString(font, "Событий пока нет", x + w / 2, contentTop() + 20, 0xFFB0B8C4);
            for (int i = start; i < game.events.size() && i < start + rows; i++) {
                GameEvent event = game.events.get(i);
                int rowY = contentTop() + (i - start) * 34;
                String target = event.targetLabel == null || event.targetLabel.isBlank() ? "" : " → " + event.targetLabel;
                String first = TIME.format(Instant.ofEpochMilli(event.timestamp)) + " · " + event.phase + " · " + event.actorLabel + target;
                String second = event.description == null ? "" : event.description;
                if (event.result != null && !event.result.isBlank()) second += " · " + event.result;
                int textWidth = game.status == GameStatus.FINISHED ? w - 32 : w - 116;
                g.drawString(font, clip(first, textWidth), x + 12, rowY, 0xFFE8EDF4);
                if (!second.isBlank()) g.drawString(font, clip(second, textWidth), x + 12, rowY + 13, 0xFFB9C3D0);
            }
        }
        if (!message.isBlank()) g.drawString(font, clip(message, w - 24), x + 12, footerTop() - 2, 0xFFFFD080);
        super.render(g, mouseX, mouseY, delta);
    }

    private String clip(String value, int pixelWidth) {
        int max = Math.max(8, pixelWidth / 6);
        return value.length() > max ? value.substring(0, max - 1) + "…" : value;
    }

    @Override public boolean isPauseScreen() { return false; }
}
