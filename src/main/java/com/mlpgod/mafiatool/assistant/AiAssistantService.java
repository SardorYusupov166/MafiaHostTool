package com.mlpgod.mafiatool.assistant;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mlpgod.mafiatool.model.GameEvent;
import com.mlpgod.mafiatool.model.GamePlayer;
import com.mlpgod.mafiatool.model.GameRecord;
import com.mlpgod.mafiatool.model.PlayerStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Read-only AI advisor. It can inspect a sanitized party snapshot but cannot mutate game state. */
public final class AiAssistantService {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final int MAX_EVENTS = 80;

    private AiAssistantService() {}

    public static CompletableFuture<String> ask(GameRecord game, List<Message> history, String prompt) {
        AiAssistantConfig config = AiAssistantConfig.load();
        String validation = validate(config);
        if (validation != null) return CompletableFuture.failedFuture(new IllegalStateException(validation));

        JsonObject payload = new JsonObject();
        payload.addProperty("model", config.model.trim());
        payload.addProperty("max_completion_tokens", 800);
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt(game)));
        for (Message entry : history) messages.add(message(entry.role, entry.content));
        messages.add(message("user", prompt));
        payload.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(URI.create(config.endpoint.trim()))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        if (!config.apiKey.isBlank()) {
            request = HttpRequest.newBuilder(URI.create(config.endpoint.trim()))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
        }

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("Сервис ИИ вернул HTTP " + response.statusCode()
                                + ". Проверьте адрес, модель и ключ.");
                    }
                    try {
                        String answer = JsonParser.parseString(response.body()).getAsJsonObject()
                                .getAsJsonArray("choices").get(0).getAsJsonObject()
                                .getAsJsonObject("message").get("content").getAsString();
                        if (answer == null || answer.isBlank()) throw new IllegalStateException("ИИ вернул пустой ответ.");
                        return answer.trim();
                    } catch (RuntimeException e) {
                        throw new IllegalStateException("Не удалось прочитать ответ сервиса ИИ.");
                    }
                });
    }

    private static JsonObject message(String role, String content) {
        JsonObject item = new JsonObject();
        item.addProperty("role", role);
        item.addProperty("content", content);
        return item;
    }

    private static String validate(AiAssistantConfig config) {
        if (config.model.isBlank()) return "Сначала укажите модель в настройках ИИ.";
        try {
            URI uri = URI.create(config.endpoint.trim());
            String scheme = uri.getScheme();
            boolean localHttp = "http".equalsIgnoreCase(scheme)
                    && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
            if (!"https".equalsIgnoreCase(scheme) && !localHttp)
                return "Адрес должен использовать HTTPS (HTTP разрешён только для localhost).";
            if (uri.getHost() == null) return "Проверьте адрес API.";
            if (config.apiKey.isBlank() && !localHttp) return "Сначала укажите API-ключ в настройках ИИ.";
        } catch (IllegalArgumentException e) {
            return "Проверьте адрес API.";
        }
        return null;
    }

    static String systemPrompt(GameRecord game) {
        StringBuilder context = new StringBuilder();
        context.append("Ты — read-only помощник ведущего в MafiaHostTool. Помогаешь кратко вести и проверять записи партии. ")
                .append("Не выдумывай события и не принимай игровые решения. Не изменяй роли, статусы, фазу, голоса или исход партии. ")
                .append("Отличай факты из журнала от предположений; если данных недостаточно, прямо скажи это. ")
                .append("В контекст переданы имена/статусы игроков и записи журнала; роли намеренно скрыты.\n\n")
                .append("Партия: ").append(safe(game.title)).append("\nФаза: ").append(game.phase)
                .append("\nСтатус: ").append(game.status).append("\nИгроки: ");
        if (game.players.isEmpty()) context.append("пока нет\n");
        else {
            context.append('\n');
            for (GamePlayer player : game.players) {
                String status = switch (player.status == null ? PlayerStatus.ALIVE : player.status) {
                    case ALIVE -> "жив";
                    case DEAD -> "мёртв";
                    case EXCLUDED -> "исключён";
                };
                context.append("- ").append(safe(player.name)).append(" — ").append(status).append('\n');
            }
        }
        context.append("\nПоследние записи журнала (из ").append(game.events.size()).append("):\n");
        int start = Math.max(0, game.events.size() - MAX_EVENTS);
        if (start > 0) context.append("[Показаны только последние ").append(MAX_EVENTS).append(" событий.]\n");
        for (int i = start; i < game.events.size(); i++) {
            GameEvent event = game.events.get(i);
            context.append(TIME.format(Instant.ofEpochMilli(event.timestamp))).append(" · ")
                    .append(event.phase).append(" · ").append(safe(event.actorLabel));
            if (event.targetLabel != null && !event.targetLabel.isBlank()) context.append(" → ").append(safe(event.targetLabel));
            context.append(" · ").append(safe(event.type == null ? "" : event.type.toString()));
            if (event.description != null && !event.description.isBlank()) context.append(" · ").append(safe(event.description));
            if (event.result != null && !event.result.isBlank()) context.append(" · результат: ").append(safe(event.result));
            if (event.correctionNote != null && !event.correctionNote.isBlank()) context.append(" · исправление: ").append(safe(event.correctionNote));
            context.append('\n');
        }
        return context.toString();
    }

    private static String safe(String value) { return value == null ? "—" : value.replace('\n', ' ').replace('\r', ' '); }

    public record Message(String role, String content) {}
}
