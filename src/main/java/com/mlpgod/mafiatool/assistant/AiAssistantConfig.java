package com.mlpgod.mafiatool.assistant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Local, per-client settings for the optional AI host assistant. */
public final class AiAssistantConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("mafiatool/ai-assistant.json");

    public String endpoint = "https://api.openai.com/v1/chat/completions";
    public String model = "";
    public String apiKey = "";

    public static AiAssistantConfig load() {
        if (!Files.exists(FILE)) return new AiAssistantConfig();
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            AiAssistantConfig config = GSON.fromJson(reader, AiAssistantConfig.class);
            if (config == null) return new AiAssistantConfig();
            if (config.endpoint == null || config.endpoint.isBlank()) config.endpoint = "https://api.openai.com/v1/chat/completions";
            if (config.model == null) config.model = "";
            if (config.apiKey == null) config.apiKey = "";
            return config;
        } catch (Exception ignored) {
            return new AiAssistantConfig();
        }
    }

    public void save() throws Exception {
        Files.createDirectories(FILE.getParent());
        Path temp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        }
        try {
            Files.move(temp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
