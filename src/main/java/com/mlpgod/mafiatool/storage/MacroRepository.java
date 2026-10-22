package com.mlpgod.mafiatool.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mlpgod.mafiatool.model.ActionMacro;
import com.mlpgod.mafiatool.model.EventType;
import com.mlpgod.mafiatool.model.MacroEffect;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Stores user macro rules independently from party and role data. */
public final class MacroRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;

    public MacroRepository() { this(FabricLoader.getInstance().getConfigDir()); }
    public MacroRepository(Path configDirectory) { this.file = configDirectory.resolve("mafiatool/macros.json"); }

    public List<ActionMacro> load() {
        if (!Files.exists(file)) return new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (Exception ignored) {
            Path backup = file.resolveSibling("macros.json.bak");
            try (Reader reader = Files.newBufferedReader(backup, StandardCharsets.UTF_8)) {
                List<ActionMacro> restored = parse(reader);
                Files.copy(backup, file, StandardCopyOption.REPLACE_EXISTING);
                return restored;
            } catch (Exception backupError) {
                return new ArrayList<>();
            }
        }
    }

    private List<ActionMacro> parse(Reader reader) {
        MacroLibrary library = GSON.fromJson(reader, MacroLibrary.class);
        if (library != null && (library.schemaVersion < 1 || library.schemaVersion > 1))
            throw new IllegalStateException("Неподдерживаемая версия схемы макросов");
        List<ActionMacro> macros = library == null || library.macros == null ? new ArrayList<>() : library.macros;
        Set<String> ids = new HashSet<>();
        macros.removeIf(macro -> macro == null);
        for (ActionMacro macro : macros) {
            if (macro.id == null || macro.id.isBlank() || !ids.add(macro.id)) macro.id = UUID.randomUUID().toString();
            if (macro.name == null || macro.name.isBlank()) macro.name = "Макрос";
            if (macro.trigger == null) macro.trigger = EventType.KILL_ATTEMPT;
            if (macro.condition == null) macro.condition = "";
            if (macro.effect == null) macro.effect = MacroEffect.ADD_LOG_NOTE;
            if (macro.textTemplate == null) macro.textTemplate = "";
        }
        return macros;
    }

    public void save(List<ActionMacro> macros) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling("macros.json.tmp");
            if (Files.exists(file)) Files.copy(file, file.resolveSibling("macros.json.bak"), StandardCopyOption.REPLACE_EXISTING);
            MacroLibrary library = new MacroLibrary();
            library.macros = macros;
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) { GSON.toJson(library, writer); }
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось сохранить макросы", e);
        }
    }

    private static final class MacroLibrary {
        int schemaVersion = 1;
        List<ActionMacro> macros = new ArrayList<>();
    }
}
