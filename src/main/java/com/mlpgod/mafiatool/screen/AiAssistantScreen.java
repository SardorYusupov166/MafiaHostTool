package com.mlpgod.mafiatool.screen;

import com.mlpgod.mafiatool.assistant.AiAssistantConfig;
import com.mlpgod.mafiatool.assistant.AiAssistantService;
import com.mlpgod.mafiatool.model.GameRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Floating, read-only AI chat that keeps the Minecraft world visible behind the panel. */
public final class AiAssistantScreen extends Screen {
    private final GameRecord game;
    private final Screen returnTo;
    private final List<AiAssistantService.Message> history = new ArrayList<>();
    private final List<String> transcript = new ArrayList<>();
    private EditBox prompt;
    private Button sendButton;
    private String status = "Подсказки ИИ не меняют данные партии.";
    private boolean busy;
    private int scroll;

    public AiAssistantScreen(GameRecord game, Screen returnTo) {
        super(Component.literal("ИИ-помощник ведущего"));
        this.game = game;
        this.returnTo = returnTo;
    }

    private int panelWidth() { return Math.min(520, width - 32); }
    private int panelHeight() { return Math.min(560, height - 32); }
    private int panelX() { return width - panelWidth() - 16; }
    private int panelY() { return 16; }
    private int messageTop() { return panelY() + 78; }
    private int messageBottom() { return panelY() + panelHeight() - 112; }

    @Override protected void init() {
        int x = panelX(), y = panelY(), w = panelWidth(), h = panelHeight();
        addRenderableWidget(Button.builder(Component.literal("Настройки ИИ"), b ->
                        Minecraft.getInstance().setScreen(new AiAssistantSettingsScreen(this)))
                .bounds(x + w - 164, y + 10, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal("×"), b -> closeToParent())
                .bounds(x + w - 38, y + 10, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> closeToParent())
                .bounds(x + 12, y + h - 32, 72, 20).build());
        prompt = new EditBox(font, x + 12, y + h - 78, w - 112, 22, Component.literal("Вопрос ведущего"));
        prompt.setHint(Component.literal("Например: проверь журнал на пропуски"));
        prompt.setMaxLength(1200);
        addRenderableWidget(prompt);
        sendButton = Button.builder(Component.literal("Отправить"), b -> send())
                .bounds(x + w - 92, y + h - 78, 80, 22).build();
        addRenderableWidget(sendButton);
        addRenderableWidget(Button.builder(Component.literal("Очистить чат"), b -> {
                    history.clear(); transcript.clear(); scroll = 0; status = "История диалога очищена.";
                }).bounds(x + w - 132, y + h - 32, 120, 20).build());
    }

    private void send() {
        if (busy || prompt == null || prompt.getValue().isBlank()) return;
        String question = prompt.getValue().trim();
        prompt.setValue("");
        transcript.add("Ведущий: " + question);
        trimTranscript();
        status = "ИИ анализирует партию…";
        busy = true;
        sendButton.active = false;
        scroll = 0;
        AiAssistantService.ask(game, List.copyOf(history), question).whenComplete((answer, error) ->
                Minecraft.getInstance().execute(() -> {
                    busy = false;
                    sendButton.active = true;
                    if (error != null) {
                        String explanation = rootMessage(error);
                        status = "Не удалось получить ответ: " + explanation;
                        transcript.add("Ошибка: " + explanation);
                    } else {
                        history.add(new AiAssistantService.Message("user", question));
                        history.add(new AiAssistantService.Message("assistant", answer));
                        while (history.size() > 20) {
                            history.remove(0);
                            if (!history.isEmpty()) history.remove(0);
                        }
                        transcript.add("Помощник: " + answer);
                        trimTranscript();
                        status = "Ответ готов. Данные партии не менялись.";
                    }
                    scroll = 0;
                }));
    }

    private void trimTranscript() {
        while (transcript.size() > 60) transcript.remove(0);
    }

    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) return "проверьте подключение и настройки ИИ";
        return message.length() > 150 ? message.substring(0, 147) + "…" : message;
    }

    private void closeToParent() { Minecraft.getInstance().setScreen(returnTo); }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= panelX() && mouseX <= panelX() + panelWidth()
                && mouseY >= messageTop() && mouseY < messageBottom()) {
            int textWidth = panelWidth() - 36;
            int totalLines = transcript.stream().mapToInt(entry -> Math.max(1, font.split(Component.literal(entry), textWidth).size())).sum();
            int visibleLines = Math.max(1, (messageBottom() - messageTop() - 8) / 11);
            scroll = Math.max(0, Math.min(Math.max(0, totalLines - visibleLines), scroll + (int) Math.signum(verticalAmount) * 3));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = panelX(), y = panelY(), w = panelWidth(), h = panelHeight();
        g.fill(x, y, x + w, y + h, 0xE010141C);
        g.fill(x, y, x + w, y + 2, 0xFF586D85);
        g.fill(x + 8, messageTop() - 6, x + w - 8, messageBottom(), 0xC0181E28);
        g.drawString(font, "ИИ-помощник · " + clip(game.title, w - 210), x + 12, y + 16, 0xFFFFFFFF);
        g.drawString(font, "Запрос уйдёт выбранному провайдеру; возможна плата за API.", x + 12, y + 43, 0xFFB5C4D6);
        g.drawString(font, "Ники, статусы и журнал отправляются; роли скрыты.", x + 12, y + 56, 0xFF9CCC8A);

        int lineHeight = 11;
        int textWidth = w - 36;
        int totalLines = transcript.stream().mapToInt(entry -> Math.max(1, font.split(Component.literal(entry), textWidth).size())).sum();
        int visibleLines = Math.max(1, (messageBottom() - messageTop() - 8) / lineHeight);
        int skip = Math.max(0, totalLines - visibleLines - scroll);
        int line = 0;
        int yText = messageTop();
        for (String entry : transcript) {
            List<net.minecraft.util.FormattedCharSequence> wrapped = font.split(Component.literal(entry), textWidth);
            if (wrapped.isEmpty()) continue;
            for (var wrappedLine : wrapped) {
                if (line++ < skip) continue;
                if (line > skip + visibleLines) break;
                g.drawString(font, wrappedLine, x + 12, yText, 0xFFE5EAF1);
                yText += lineHeight;
            }
        }
        if (transcript.isEmpty()) g.drawString(font, "Задайте вопрос по текущей партии.", x + 12, messageTop() + 8, 0xFFB0B8C4);
        g.drawString(font, clip(status, w - 24), x + 12, y + h - 96, 0xFFFFD080);
        super.render(g, mouseX, mouseY, delta);
    }

    private String clip(String value, int pixelWidth) {
        int max = Math.max(8, pixelWidth / 6);
        return value.length() > max ? value.substring(0, max - 1) + "…" : value;
    }

    @Override public boolean isPauseScreen() { return false; }
}
