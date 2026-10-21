package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.*;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Explicit review checkpoint before the one-time random assignment. */
public final class GameReviewScreen extends Screen {
    private final GameRecord game;
    private final Screen returnTo;
    private final GameService service = GameService.get();
    private PhaseKind initialPhase = PhaseKind.DAY;
    private String message = "";
    private boolean messageError;
    private int scroll;
    private int selectedPlayer;
    private int detailsScroll;

    public GameReviewScreen(GameRecord game, Screen returnTo) {
        super(Component.literal("Проверка состава")); this.game = game; this.returnTo = returnTo;
    }

    @Override protected void init() {
        int left = 16;
        int availableWidth = Math.max(120, width - 32);
        int gap = 6;
        int firstWidth = Math.max(72, (availableWidth - gap * 2) / 4);
        int secondWidth = firstWidth;
        int thirdWidth = Math.max(40, availableWidth - firstWidth - secondWidth - gap * 2);
        int visible = visiblePlayers();
        scroll = Math.min(scroll, Math.max(0, game.players.size() - visible));
        if (!game.players.isEmpty()) selectedPlayer = Math.max(0, Math.min(selectedPlayer, game.players.size() - 1));
        int rowTop = 112;
        for (int i = scroll; i < game.players.size() && i < scroll + visible; i++) {
            GamePlayer player = game.players.get(i);
            String role = player.role == null ? "Будет назначена случайно" : player.role.name;
            int y = rowTop + (i - scroll) * 24;
            int playerIndex = i;
            addRenderableWidget(Button.builder(Component.literal(clip(player.name, firstWidth - 14)), b -> selectPlayer(playerIndex))
                    .bounds(left + 4, y, firstWidth - 8, 22).build());
            addRenderableWidget(Button.builder(Component.literal(clip(role, secondWidth - 14)), b -> selectPlayer(playerIndex))
                    .bounds(left + firstWidth + gap + 4, y, secondWidth - 8, 22).build());
        }
        if (game.players.size() > visible) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> { scroll = Math.max(0, scroll - 1); rebuildWidgets(); })
                    .bounds(left + firstWidth - 24, 88, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> { scroll = Math.min(game.players.size() - visible, scroll + 1); rebuildWidgets(); })
                    .bounds(left + firstWidth - 24, height - 132, 20, 20).build());
        }
        int bottom = height - 40;
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> Minecraft.getInstance().setScreen(returnTo))
                .bounds(left, bottom, 86, 24).build());
        if (!game.rolesAssigned) {
            addRenderableWidget(Button.builder(Component.literal("Распределить роли"), b -> distribute())
                    .bounds(left + 92, bottom, 144, 24).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Перераспределить…"), b -> redistribute())
                    .bounds(left + 92, bottom, 144, 24).build());
            addRenderableWidget(Button.builder(Component.literal("Начать: " + phaseName()), b -> {
                initialPhase = initialPhase == PhaseKind.DAY ? PhaseKind.NIGHT : PhaseKind.DAY;
                b.setMessage(Component.literal("Начать: " + phaseName()));
            }).bounds(left + 242, bottom, 108, 24).build());
            addRenderableWidget(Button.builder(Component.literal("Начать партию"), b -> start())
                    .bounds(left + 356, bottom, 118, 24).build());
        }
    }

    private int visiblePlayers() {
        return Math.max(1, Math.min(12, (height - 244) / 24));
    }

    private void selectPlayer(int index) {
        selectedPlayer = index;
        detailsScroll = 0;
        rebuildWidgets();
    }

    private void distribute() {
        List<String> errors = service.validationErrors(game);
        if (!errors.isEmpty()) {
            message = String.join(" · ", errors);
            messageError = true;
            rebuildWidgets();
            return;
        }
        try {
            service.distributeRoles(game);
            message = "Роли распределены и сохранены";
            messageError = false;
        } catch (Exception e) {
            message = e.getMessage() == null ? "Не удалось распределить роли" : e.getMessage();
            messageError = true;
        }
        rebuildWidgets();
    }

    private void redistribute() {
        Minecraft.getInstance().setScreen(new ConfirmScreen(yes -> {
            if (yes) try { service.redistributeRoles(game); message = "Раздача заменена"; messageError = false; }
            catch (Exception e) { message = e.getMessage(); messageError = true; }
            Minecraft.getInstance().setScreen(this); rebuildWidgets();
        }, Component.literal("Перераспределить роли?"), Component.literal("Текущая раздача будет заменена до начала партии."), Component.literal("Перераспределить"), Component.literal("Отмена")));
    }

    private void start() {
        try {
            service.startGame(game, initialPhase);
            Minecraft.getInstance().setScreen(new MafiaGameScreen(game));
        } catch (Exception e) { message = e.getMessage(); messageError = true; }
    }

    private String phaseName() { return initialPhase == PhaseKind.DAY ? "День" : "Ночь"; }

    @Override public void render(GuiGraphics g, int x, int y, float d) {
        g.fill(0, 0, width, height, 0xA0101010);
        g.drawCenteredString(font, "Проверка состава · " + game.title, width / 2, 18, 0xFFFFFF);
        int players = game.players.size(), total = service.enabledRoleCount(game);
        g.drawCenteredString(font, "Участников: " + players + " · выбранных ролей: " + total + (players == total ? " · совпадает" : " · не совпадает"), width / 2, 43,
                players == total ? 0xAADD99 : 0xFF8888);
        g.drawCenteredString(font, "Каждый шаблон роли будет скопирован в партию; последующие правки шаблонов не изменят эту раздачу.", width / 2, 61, 0xA0A0A0);
        String composition = game.rolePlan == null ? "Роли партии не настроены" : game.rolePlan.stream()
                .filter(selection -> selection.enabled)
                .map(selection -> selection.role.name + " × " + selection.count)
                .reduce((a, b) -> a + " · " + b).orElse("Нет включённых ролей");
        g.drawCenteredString(font, clip(composition, Math.max(80, width - 32)), width / 2, 77, 0xC0C0C0);
        int left = 16;
        int gap = 6;
        int availableWidth = Math.max(120, width - 32);
        int firstWidth = Math.max(72, (availableWidth - gap * 2) / 4);
        int secondWidth = firstWidth;
        int thirdWidth = Math.max(40, availableWidth - firstWidth - secondWidth - gap * 2);
        int panelTop = 88;
        int panelBottom = height - 104;
        int rowTop = 112;
        drawPanel(g, left, panelTop, firstWidth, panelBottom, "Участники");
        drawPanel(g, left + firstWidth + gap, panelTop, secondWidth, panelBottom, "Роли игроков");
        int detailsLeft = left + firstWidth + secondWidth + gap * 2;
        drawPanel(g, detailsLeft, panelTop, thirdWidth, panelBottom, "Описание роли");
        drawSelectedRole(g, detailsLeft + 10, panelTop + 31, thirdWidth - 20, panelBottom - panelTop - 42);
        if (!message.isBlank()) {
            var lines = font.split(Component.literal(message), Math.max(80, width - 32));
            int statusY = height - 96;
            int color = messageError ? 0xFFFF8888 : 0xAADD99;
            for (int i = 0; i < Math.min(lines.size(), 4); i++) {
                g.drawCenteredString(font, lines.get(i), width / 2, statusY + i * 10, color);
            }
        } else if (game.rolesAssigned) {
            g.drawCenteredString(font, "Раздача сохранена · выбери игрока, чтобы посмотреть описание роли", width / 2, height - 92, 0xAADD99);
        }
        super.render(g, x, y, d);
        drawOutline(g, left + 2, rowTop + (selectedPlayer - scroll) * 24, firstWidth - 4, 22, 0xFFFF5555);
        drawOutline(g, left + firstWidth + gap + 2, rowTop + (selectedPlayer - scroll) * 24, secondWidth - 4, 22, 0xFFFF5555);
    }

    private void drawPanel(GuiGraphics g, int x, int top, int panelWidth, int bottom, String title) {
        g.fill(x, top, x + panelWidth, bottom, 0x80202020);
        drawOutline(g, x, top, panelWidth, bottom - top, 0xFFFF4444);
        g.drawCenteredString(font, title, x + panelWidth / 2, top + 7, 0xFFFF5555);
    }

    private void drawSelectedRole(GuiGraphics g, int x, int y, int panelWidth, int panelHeight) {
        if (game.players.isEmpty()) {
            g.drawString(font, "Сначала добавь участников.", x, y, 0xB0B0B0);
            return;
        }
        GamePlayer player = game.players.get(selectedPlayer);
        if (player.role == null) {
            g.drawString(font, player.name, x, y, 0xFFFFFF);
            g.drawString(font, "Роль ещё не распределена.", x, y + 16, 0xB0B0B0);
            g.drawString(font, "После раздачи здесь появятся", x, y + 34, 0x909090);
            g.drawString(font, "команда, фаза и полное описание.", x, y + 46, 0x909090);
            return;
        }
        RoleSnapshot role = player.role;
        String team = role.team == null ? "Не указана" : switch (role.team) {
            case TOWN -> "Мирные"; case MAFIA -> "Мафия"; case NEUTRAL -> "Нейтральные"; case CUSTOM -> role.customTeam == null || role.customTeam.isBlank() ? "Своя команда" : role.customTeam;
        };
        String phase = role.actionPhase == null ? "Любая" : switch (role.actionPhase) {
            case DAY -> "День"; case NIGHT -> "Ночь"; case ANY -> "Любая";
        };
        String details = player.name + "\nРоль: " + role.name + "\nКоманда: " + team + "\nФаза действия: " + phase + "\n\n"
                + (role.description == null || role.description.isBlank() ? "Описание не задано." : role.description);
        var lines = font.split(Component.literal(details), Math.max(40, panelWidth));
        int maxLines = Math.max(1, panelHeight / 10);
        detailsScroll = Math.min(detailsScroll, Math.max(0, lines.size() - maxLines));
        for (int i = detailsScroll; i < lines.size() && i < detailsScroll + maxLines; i++) {
            g.drawString(font, lines.get(i), x, y + (i - detailsScroll) * 10, 0xFFE0E0E0);
        }
    }

    private void drawOutline(GuiGraphics g, int x, int y, int outlineWidth, int outlineHeight, int color) {
        if (outlineWidth <= 0 || outlineHeight <= 0) return;
        g.fill(x, y, x + outlineWidth, y + 1, color);
        g.fill(x, y + outlineHeight - 1, x + outlineWidth, y + outlineHeight, color);
        g.fill(x, y, x + 1, y + outlineHeight, color);
        g.fill(x + outlineWidth - 1, y, x + outlineWidth, y + outlineHeight, color);
    }
    private String clip(String value, int pixelWidth) {
        int max = Math.max(12, pixelWidth / 6);
        return value.length() > max ? value.substring(0, max - 1) + "…" : value;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
        int left = 16;
        int availableWidth = Math.max(120, width - 32);
        int gap = 6;
        int firstWidth = Math.max(72, (availableWidth - gap * 2) / 4);
        int secondWidth = firstWidth;
        int detailsLeft = left + firstWidth + secondWidth + gap * 2;
        int detailsWidth = availableWidth - firstWidth - secondWidth - gap * 2;
        if (x >= detailsLeft && x < detailsLeft + detailsWidth && y >= 88 && y < height - 104) {
            detailsScroll = Math.max(0, detailsScroll - (int) Math.signum(vertical));
            return true;
        }
        int visible = visiblePlayers();
        if (x >= left && x < detailsLeft && y >= 88 && y < height - 104 && game.players.size() > visible) {
            scroll = Math.max(0, Math.min(game.players.size() - visible, scroll - (int) Math.signum(vertical)));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public boolean isPauseScreen() { return false; }
}
