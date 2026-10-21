package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.GameRecord;
import com.mlpgod.mafiatool.model.GameStatus;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class MafiaGamesScreen extends Screen {
    private static final GameService SERVICE = GameService.get();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm").withZone(ZoneId.systemDefault());
    private boolean archive;
    private int scroll;
    private String message = "";

    public MafiaGamesScreen() { super(Component.literal("MafiaHostTool · партии")); }

    @Override protected void init() {
        List<GameRecord> games = visibleGames();
        int rowHeight = 48;
        int visible = Math.max(1, (height - 150) / rowHeight);
        scroll = Math.min(scroll, Math.max(0, games.size() - visible));
        int left = Math.max(12, width / 2 - 310);
        int right = Math.min(width - 12, left + 620);

        addRenderableWidget(Button.builder(Component.literal("Активные"), b -> { archive = false; scroll = 0; rebuildWidgets(); })
                .bounds(left, 48, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("История"), b -> { archive = true; scroll = 0; rebuildWidgets(); })
                .bounds(left + 106, 48, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Создать партию"), b -> {
            GameRecord game = SERVICE.createGame("Новая партия");
            Minecraft.getInstance().setScreen(new MafiaSetupScreen(game));
        }).bounds(right - 232, 48, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Роли"), b -> Minecraft.getInstance().setScreen(new RoleSettingsScreen()))
                .bounds(right - 114, 48, 114, 20).build());

        for (int i = scroll; i < games.size() && i < scroll + visible; i++) {
            GameRecord game = games.get(i);
            int y = 78 + (i - scroll) * rowHeight;
            int rowW = right - left;
            int openW = Math.max(130, rowW - 208);
            addRenderableWidget(Button.builder(Component.literal(game.title), b -> Minecraft.getInstance().setScreen(
                    game.status == GameStatus.PREPARING ? new MafiaSetupScreen(game) : new MafiaGameScreen(game)))
                    .bounds(left, y, openW, 24).build());
            if (game.status != GameStatus.FINISHED)
                addRenderableWidget(Button.builder(Component.literal("✎"), b -> Minecraft.getInstance().setScreen(new RenameGameScreen(game, this)))
                        .bounds(left + openW + 4, y, 30, 24).build());
            if (game.status == GameStatus.FINISHED) {
                addRenderableWidget(Button.builder(Component.literal("Восстановить"), b -> confirmReopen(game))
                        .bounds(left + openW + 38, y, 88, 24).build());
            } else if (game.status == GameStatus.ACTIVE) {
                addRenderableWidget(Button.builder(Component.literal("Завершить"), b -> Minecraft.getInstance().setScreen(new FinishGameScreen(game)))
                        .bounds(left + openW + 38, y, 88, 24).build());
            }
            addRenderableWidget(Button.builder(Component.literal("⇩"), b -> exportGame(game))
                    .bounds(left + rowW - 68, y, 30, 24).build());
            addRenderableWidget(Button.builder(Component.literal("×"), b -> confirmDelete(game))
                    .bounds(left + rowW - 34, y, 34, 24).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Импорт из буфера"), b -> importClipboard())
                .bounds(left, height - 62, 142, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
                .bounds(width - 88, height - 34, 70, 22).build());
        if (games.size() > visible) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> { scroll = Math.max(0, scroll - 1); rebuildWidgets(); })
                    .bounds(right - 24, 75, 24, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> { scroll = Math.min(games.size() - visible, scroll + 1); rebuildWidgets(); })
                    .bounds(right - 24, height - 88, 24, 20).build());
        }
    }

    private List<GameRecord> visibleGames() { return archive ? SERVICE.archivedGames() : SERVICE.activeGames(); }

    private void confirmDelete(GameRecord game) {
        Minecraft.getInstance().setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                try { SERVICE.deleteGame(game); message = "Партия удалена"; }
                catch (Exception e) { message = e.getMessage(); }
            }
            Minecraft.getInstance().setScreen(this);
        }, Component.literal("Удалить партию?"), Component.literal("Будут удалены файл партии и его текущая запись из списка."), Component.literal("Удалить"), Component.literal("Отмена")));
    }

    private void confirmReopen(GameRecord game) {
        Minecraft.getInstance().setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                try { SERVICE.reopenGame(game); message = "Партия восстановлена"; }
                catch (Exception e) { message = e.getMessage(); }
            }
            Minecraft.getInstance().setScreen(this);
        }, Component.literal("Восстановить партию?"), Component.literal("Партия вернётся в активные и станет доступна для изменений."), Component.literal("Восстановить"), Component.literal("Отмена")));
    }

    private void exportGame(GameRecord game) {
        Minecraft.getInstance().keyboardHandler.setClipboard(SERVICE.exportGame(game));
        message = "JSON партии «" + game.title + "» скопирован в буфер обмена";
    }

    private void importClipboard() {
        String json = Minecraft.getInstance().keyboardHandler.getClipboard();
        try {
            GameRecord game = SERVICE.importGame(json);
            message = "Импортирована партия «" + game.title + "»";
            archive = game.status == GameStatus.FINISHED;
            rebuildWidgets();
        } catch (Exception e) { message = "Импорт не выполнен: " + e.getMessage(); }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xA0101010);
        g.drawCenteredString(font, "MafiaHostTool · первый релиз", width / 2, 18, 0xFFFFFF);
        g.drawCenteredString(font, archive ? "История партий" : "Активные партии", width / 2, 34, 0xC0C0C0);
        List<GameRecord> games = visibleGames();
        int left = Math.max(12, width / 2 - 310);
        int rowW = Math.min(width - 24, 620);
        for (int i = scroll; i < games.size() && i < scroll + Math.max(1, (height - 150) / 48); i++) {
            GameRecord game = games.get(i);
            int y = 78 + (i - scroll) * 48;
            String phase = game.phase == null ? "Подготовка" : game.phase.toString();
            String state = game.status == GameStatus.PREPARING ? "Подготовка" : game.status == GameStatus.FINISHED ? "Завершена" : "Идёт";
            String created = DATE.format(Instant.ofEpochMilli(game.createdAt));
            String updated = DATE.format(Instant.ofEpochMilli(game.updatedAt));
            String details = state + " · " + game.players.size() + " игроков · " + game.aliveCount() + " живы · " + phase
                    + " · создана " + created + " · изм. " + updated;
            g.drawString(font, font.substrByWidth(details, rowW - 8),
                    left, y + 29, 0xAAAAAA);
        }
        if (games.isEmpty()) g.drawCenteredString(font, archive ? "Архив пока пуст" : "Активных партий пока нет", width / 2, height / 2, 0xC0C0C0);
        List<String> errors = SERVICE.recoveryMessages();
        if (!errors.isEmpty()) g.drawString(font, errors.get(0), left, height - 82, 0xFFAA55);
        if (!message.isEmpty()) g.drawString(font, message, left + 310, height - 58, 0xFFFFFF);
        super.render(g, mouseX, mouseY, delta);
    }

    @Override public boolean mouseScrolled(double x, double y, double hx, double vy) {
        int visible = Math.max(1, (height - 150) / 48);
        List<GameRecord> games = visibleGames();
        if (games.size() > visible) {
            scroll = Math.max(0, Math.min(games.size() - visible, scroll - (int) Math.signum(vy)));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(x, y, hx, vy);
    }

    @Override public boolean isPauseScreen() { return false; }
}
