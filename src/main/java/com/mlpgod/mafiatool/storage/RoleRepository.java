package com.mlpgod.mafiatool.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mlpgod.mafiatool.model.RoleDefinition;
import com.mlpgod.mafiatool.model.RoleTeam;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;

public final class RoleRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final Path legacyFile;

    public RoleRepository() { this(FabricLoader.getInstance().getConfigDir()); }
    public RoleRepository(Path configDirectory) {
        this.file = configDirectory.resolve("mafiatool/roles.json");
        this.legacyFile = configDirectory.resolve("mafiatool-roles.txt");
    }

    public List<RoleDefinition> load() {
        if (!Files.exists(file)) return loadLegacy();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parseRoles(reader);
        } catch (Exception ignored) {
            Path backup = file.resolveSibling("roles.json.bak");
            try (Reader reader = Files.newBufferedReader(backup, StandardCharsets.UTF_8)) {
                List<RoleDefinition> restored = parseRoles(reader);
                Files.copy(backup, file, StandardCopyOption.REPLACE_EXISTING);
                return restored;
            } catch (Exception backupError) {
                try { Files.copy(file, file.resolveSibling("roles.json.corrupt.bak"), StandardCopyOption.REPLACE_EXISTING); } catch (Exception alsoIgnored) {}
            }
            return loadLegacy();
        }
    }

    private List<RoleDefinition> parseRoles(Reader reader) {
        RoleLibrary library = GSON.fromJson(reader, RoleLibrary.class);
        if (library != null && (library.schemaVersion < 1 || library.schemaVersion > 1))
            throw new IllegalStateException("Неподдерживаемая версия схемы ролей");
        return library == null || library.roles == null ? defaults() : library.roles;
    }

    public void save(List<RoleDefinition> roles) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling("roles.json.tmp");
            if (Files.exists(file)) Files.copy(file, file.resolveSibling("roles.json.bak"), StandardCopyOption.REPLACE_EXISTING);
            RoleLibrary library = new RoleLibrary();
            library.roles = roles;
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) { GSON.toJson(library, writer); }
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось сохранить роли", e);
        }
    }

    private List<RoleDefinition> loadLegacy() {
        if (!Files.exists(legacyFile)) return defaults();
        try {
            List<RoleDefinition> result = new ArrayList<>();
            for (String line : Files.readAllLines(legacyFile, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\t", -1);
                if (parts.length != 3) continue;
                String name = new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                int count = Math.max(1, Integer.parseInt(parts[2]));
                result.add(new RoleDefinition(name, RoleTeam.TOWN, count, Boolean.parseBoolean(parts[1])));
            }
            if (!result.isEmpty()) {
                save(result);
                Files.move(legacyFile, legacyFile.resolveSibling("mafiatool-roles.txt.migrated.bak"), StandardCopyOption.REPLACE_EXISTING);
                return result;
            }
        } catch (Exception ignored) {}
        return defaults();
    }

    private static final class RoleLibrary {
        int schemaVersion = 1;
        List<RoleDefinition> roles = new ArrayList<>();
    }

    private List<RoleDefinition> defaults() {
        List<RoleDefinition> roles = new ArrayList<>();
        roles.add(new RoleDefinition("Мафия", com.mlpgod.mafiatool.model.RoleTeam.MAFIA, 2, true));
        roles.add(new RoleDefinition("Доктор", com.mlpgod.mafiatool.model.RoleTeam.TOWN, 1, true));
        roles.add(new RoleDefinition("Комиссар", com.mlpgod.mafiatool.model.RoleTeam.TOWN, 1, true));
        return roles;
    }
}
