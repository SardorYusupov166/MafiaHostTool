package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.model.*;
import com.mlpgod.mafiatool.service.GameService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VotingScreen extends Screen {
    private final GameRecord game;
    private final List<GamePlayer> alive;
    private final Map<String, Integer> selections = new HashMap<>();
    private int scroll;
    private String message = "";

    public VotingScreen(GameRecord game) {
        super(Component.literal("Дневное голосование"));
        this.game = game;
        this.alive = game.players.stream().filter(p -> p.status == PlayerStatus.ALIVE).toList();
    }

    @Override protected void init() {
        int left = Math.max(18, width / 2 - 260);
        int visible = Math.max(1, (height - 178) / 28);
        scroll = Math.min(scroll, Math.max(0, alive.size() - visible));
        for (int i = scroll; i < alive.size() && i < scroll + visible; i++) {
            GamePlayer voter = alive.get(i);
            int y = 68 + (i - scroll) * 28;
            int selection = selections.getOrDefault(voter.id, -1);
            String chosen = selection < 0 ? "Воздержался" : alive.get(selection).name;
            addRenderableWidget(Button.builder(Component.literal(voter.name), b -> {})
                    .bounds(left, y, 140, 22).build());
            addRenderableWidget(Button.builder(Component.literal("→ " + chosen), b -> {
                int next = selections.getOrDefault(voter.id, -1) + 1;
                if (next >= alive.size()) next = -1;
                selections.put(voter.id, next);
                b.setMessage(Component.literal("→ " + (next < 0 ? "Воздержался" : alive.get(next).name)));
            }).bounds(left + 146, y, 180, 22).build());
            addRenderableWidget(Button.builder(Component.literal("Записать"), b -> cast(voter, selections.getOrDefault(voter.id, -1)))
                    .bounds(left + 332, y, 84, 22).build());
        }
        int bottom = height - 40;
        addRenderableWidget(Button.builder(Component.literal("Зафиксировать итог"), b -> saveResult())
                .bounds(left, bottom, 142, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Исключить единственного лидера"), b -> exileLeader())
                .bounds(left + 148, bottom, 192, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> Minecraft.getInstance().setScreen(new MafiaGameScreen(game)))
                .bounds(width - 90, bottom, 72, 24).build());
        if (alive.size() > visible) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> { scroll = Math.max(0, scroll - 1); rebuildWidgets(); }).bounds(left + 422, 66, 22, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> { scroll = Math.min(alive.size() - visible, scroll + 1); rebuildWidgets(); }).bounds(left + 422, height - 68, 22, 20).build());
        }
    }

    private void cast(GamePlayer voter, int targetIndex) {
        try {
            String target = targetIndex < 0 ? null : alive.get(targetIndex).id;
            GameService.get().castVote(game, voter.id, target);
            message = "Голос " + voter.name + " записан";
        } catch (Exception e) { message = e.getMessage(); }
    }

    private void saveResult() {
        GameService service = GameService.get();
        Map<String, Integer> tally = service.voteTally(game, game.phase.number);
        List<String> leaders = service.voteLeaders(game, game.phase.number);
        String summary;
        if (leaders.isEmpty()) summary = "Голосование без голосов за игроков";
        else if (leaders.size() > 1) summary = "Ничья: " + leaders.stream().map(this::nameOf).toList();
        else summary = "Лидер голосования: " + nameOf(leaders.get(0)) + " — " + tally.get(leaders.get(0)) + " голосов";
        try { service.recordVoteResult(game, summary, "Ведущий фиксирует исход вручную"); message = summary; }
        catch (Exception e) { message = e.getMessage(); }
    }

    private void exileLeader() {
        GameService service = GameService.get();
        List<String> leaders = service.voteLeaders(game, game.phase.number);
        if (leaders.size() != 1) { message = leaders.isEmpty() ? "Нет лидера" : "Ничья: решение принимает ведущий"; return; }
        GamePlayer player = game.players.stream().filter(p -> p.id.equals(leaders.get(0))).findFirst().orElse(null);
        if (player == null) { message = "Игрок не найден"; return; }
        Minecraft.getInstance().setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                try {
                    service.setPlayerStatus(game, player.id, PlayerStatus.EXCLUDED, "Исключён по итогам голосования");
                    service.recordVoteResult(game, "По итогам голосования исключён " + player.name, "Подтверждено ведущим");
                    Minecraft.getInstance().setScreen(new MafiaGameScreen(game));
                    return;
                } catch (Exception e) { message = e.getMessage(); }
            }
            Minecraft.getInstance().setScreen(this);
        }, Component.literal("Исключить игрока?"), Component.literal(player.name + " получит статус «Исключён». Решение можно изменить в панели ведущего."), Component.literal("Исключить"), Component.literal("Отмена")));
    }

    private String nameOf(String id) { return game.players.stream().filter(p -> p.id.equals(id)).map(p -> p.name).findFirst().orElse("?"); }

    @Override public void render(GuiGraphics g, int x, int y, float d) {
        g.fill(0, 0, width, height, 0xA0101010);
        g.drawCenteredString(font, "Голосование · День " + game.phase.number, width / 2, 18, 0xFFFFFF);
        int left = Math.max(18, width / 2 - 260);
        Map<String, Integer> tally = GameService.get().voteTally(game, game.phase.number);
        g.drawString(font, "Текущий подсчёт:", left, 42, 0xFFFFFF);
        String countText = tally.isEmpty() ? "Голосов пока нет" : tally.entrySet().stream().map(e -> nameOf(e.getKey()) + " — " + e.getValue()).reduce((a, b) -> a + " · " + b).orElse("");
        g.drawString(font, countText.length() > 95 ? countText.substring(0, 94) + "…" : countText, left + 100, 42, 0xB0B0B0);
        if (!message.isBlank()) g.drawString(font, message, left, height - 60, 0xFFFFFF);
        super.render(g, x, y, d);
    }
    @Override public boolean isPauseScreen() { return false; }
}
