package com.mlpgod.mafiatool.assistant;

import com.mlpgod.mafiatool.model.GameRecord;
import com.mlpgod.mafiatool.model.GameStatus;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

/** Opt-in, session-only observer for visible Minecraft chat. */
public final class AiChatMonitor {
    private static final int MAX_LINES = 60;
    private static final int ANALYSIS_LINES = 20;
    private static final int NEW_LINES_PER_ANALYSIS = 5;
    private static final long MIN_INTERVAL_MS = 20_000;
    private static final ArrayDeque<String> chat = new ArrayDeque<>();
    private static final ArrayDeque<String> suggestions = new ArrayDeque<>();
    private static GameRecord game;
    private static boolean enabled;
    private static boolean busy;
    private static int linesSinceAnalysis;
    private static int generation;
    private static long lastAnalysisAt;
    private static long lastCapturedAt;
    private static String lastCapturedText = "";

    private AiChatMonitor() {}

    public static boolean isEnabledFor(GameRecord requestedGame) {
        return enabled && game != null && game.id.equals(requestedGame.id);
    }

    public static String toggle(GameRecord requestedGame) {
        if (isEnabledFor(requestedGame)) {
            stop();
            return "Наблюдение за игровым чатом выключено.";
        }
        String problem = AiAssistantService.configurationProblem();
        if (problem != null) return problem;
        stop();
        game = requestedGame;
        enabled = true;
        return "Наблюдение включено. Анализ после 5 новых сообщений.";
    }

    public static void stop() {
        generation++;
        enabled = false;
        busy = false;
        game = null;
        chat.clear();
        suggestions.clear();
        linesSinceAnalysis = 0;
        lastCapturedText = "";
        lastCapturedAt = 0;
    }

    public static void capture(String text) {
        if (!enabled || game == null || game.status == GameStatus.FINISHED || text == null) return;
        String clean = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (clean.isBlank()) return;
        if (clean.length() > 400) clean = clean.substring(0, 397) + "…";

        long now = System.currentTimeMillis();
        // CHAT and GAME callbacks can expose the same visible line on some servers.
        if (clean.equals(lastCapturedText) && now - lastCapturedAt < 750) return;
        lastCapturedText = clean;
        lastCapturedAt = now;
        chat.addLast(clean);
        while (chat.size() > MAX_LINES) chat.removeFirst();
        linesSinceAnalysis++;
    }

    public static void tick(Minecraft client) {
        if (!enabled) return;
        if (client.getConnection() == null || game == null || game.status == GameStatus.FINISHED) {
            stop();
            return;
        }
        long now = System.currentTimeMillis();
        if (busy || linesSinceAnalysis < NEW_LINES_PER_ANALYSIS || now - lastAnalysisAt < MIN_INTERVAL_MS) return;

        List<String> batch = new ArrayList<>(chat);
        if (batch.size() > ANALYSIS_LINES) batch = batch.subList(batch.size() - ANALYSIS_LINES, batch.size());
        GameRecord requestedGame = game;
        int requestGeneration = generation;
        busy = true;
        linesSinceAnalysis = 0;
        lastAnalysisAt = now;
        AiAssistantService.analyzeChat(requestedGame, List.copyOf(batch)).whenComplete((answer, error) ->
                client.execute(() -> {
                    if (requestGeneration != generation || !enabled) return;
                    busy = false;
                    if (error != null) {
                        String message = rootMessage(error);
                        suggestions.addLast("Ошибка анализа чата: " + message);
                    } else if (answer != null && !answer.isBlank() && !answer.trim().equalsIgnoreCase("[NO_ACTION]")) {
                        suggestions.addLast(answer.trim());
                    }
                    while (suggestions.size() > 10) suggestions.removeFirst();
                }));
    }

    public static String pollSuggestion(GameRecord requestedGame) {
        if (!isEnabledFor(requestedGame)) return null;
        return suggestions.pollFirst();
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause.getCause() != null) && cause.getCause() != null)
            cause = cause.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) return "проверьте подключение и настройки ИИ";
        return message.length() > 120 ? message.substring(0, 117) + "…" : message;
    }
}
