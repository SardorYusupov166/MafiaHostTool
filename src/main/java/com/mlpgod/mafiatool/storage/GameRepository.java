package com.mlpgod.mafiatool.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mlpgod.mafiatool.model.GameRecord;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import com.mlpgod.mafiatool.model.EventType;
import com.mlpgod.mafiatool.model.GameEvent;
import com.mlpgod.mafiatool.model.GamePhase;
import com.mlpgod.mafiatool.model.GamePlayer;
import com.mlpgod.mafiatool.model.GameStatus;
import com.mlpgod.mafiatool.model.PhaseKind;
import com.mlpgod.mafiatool.model.PlayerStatus;
import com.mlpgod.mafiatool.model.RoleDefinition;
import com.mlpgod.mafiatool.model.RoleSnapshot;
import com.mlpgod.mafiatool.model.RoleTeam;

/** One JSON file per party. A corrupt party file cannot prevent other parties loading. */
public final class GameRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path configDirectory;
    private final Path directory;
    private final List<String> recoveryMessages = new ArrayList<>();

    public GameRepository() { this(FabricLoader.getInstance().getConfigDir()); }
    public GameRepository(Path configDirectory) {
        this.configDirectory = configDirectory;
        this.directory = configDirectory.resolve("mafiatool/games");
    }

    public List<GameRecord> loadAll() {
        recoveryMessages.clear();
        List<GameRecord> records = new ArrayList<>();
        try {
            Files.createDirectories(directory);
            migrateLegacyFile();
            try (var paths = Files.list(directory)) {
                paths.filter(p -> p.getFileName().toString().endsWith(".json"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(path -> readOne(path, records));
            }
        } catch (Exception e) {
            recoveryMessages.add("Не удалось открыть каталог партий: " + e.getMessage());
        }
        records.sort(Comparator.comparingLong((GameRecord g) -> g.updatedAt).reversed());
        return records;
    }

    private void readOne(Path path, List<GameRecord> out) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            out.add(parseGame(reader));
        } catch (Exception e) {
            Path backup = path.resolveSibling(path.getFileName() + ".bak");
            try (Reader reader = Files.newBufferedReader(backup, StandardCharsets.UTF_8)) {
                out.add(parseGame(reader));
                Files.copy(backup, path, StandardCopyOption.REPLACE_EXISTING);
                recoveryMessages.add("Партия восстановлена из резервной копии " + backup.getFileName());
            } catch (Exception backupError) {
                recoveryMessages.add("Партия " + path.getFileName() + " повреждена; остальные партии загружены. Резервная копия также недоступна.");
            }
        }
    }

    private GameRecord parseGame(Reader reader) {
        GameRecord record = GSON.fromJson(reader, GameRecord.class);
        if (record == null || record.id == null || record.id.isBlank()) throw new IllegalStateException("нет ID партии");
        if (record.schemaVersion < 1 || record.schemaVersion > 1) throw new IllegalStateException("неподдерживаемая версия схемы " + record.schemaVersion);
        boolean repaired = false;
        if (record.title == null || record.title.isBlank()) { record.title = "Игра"; repaired = true; }
        if (record.status == null) { record.status = GameStatus.PREPARING; repaired = true; }
        if (record.players == null) record.players = new ArrayList<>();
        else repaired |= record.players.removeIf(player -> player == null);
        for (GamePlayer player : record.players) {
            if (player.id == null || player.id.isBlank()) { player.id = UUID.randomUUID().toString(); repaired = true; }
            if (player.name == null || player.name.isBlank()) { player.name = "Игрок"; repaired = true; }
            if (player.status == null) { player.status = PlayerStatus.ALIVE; repaired = true; }
        }
        if (record.rolePlan == null) record.rolePlan = new ArrayList<>();
        else repaired |= record.rolePlan.removeIf(selection -> selection == null || selection.role == null);
        if (record.events == null) record.events = new ArrayList<>();
        else repaired |= record.events.removeIf(event -> event == null);
        if (record.votes == null) record.votes = new ArrayList<>();
        else repaired |= record.votes.removeIf(vote -> vote == null);
        if (record.phase == null || record.phase.kind == null) { record.phase = new GamePhase(); repaired = true; }
        for (GameEvent event : record.events) {
            if (event.phase == null || event.phase.kind == null) { event.phase = new GamePhase(record.phase.kind, record.phase.number); repaired = true; }
            if (event.type == null) { event.type = EventType.ACTION; repaired = true; }
            if (event.actorLabel == null) event.actorLabel = "Ведущий";
            if (event.targetLabel == null) event.targetLabel = "";
            if (event.description == null) event.description = "";
            if (event.result == null) event.result = "";
        }
        if (repaired) recoveryMessages.add("В сохранении партии исправлены пустые записи: " + record.title);
        return record;
    }

    /** Best-effort migration from the single-file prototype used by earlier builds. */
    private void migrateLegacyFile() {
        Path oldFile = configDirectory.resolve("mafiatool-games.json");
        if (!Files.exists(oldFile)) return;
        try (Reader reader = Files.newBufferedReader(oldFile, StandardCharsets.UTF_8)) {
            List<LegacyGame> oldGames = GSON.fromJson(reader, new com.google.gson.reflect.TypeToken<List<LegacyGame>>() {}.getType());
            if (oldGames == null || oldGames.isEmpty()) return;
            for (LegacyGame old : oldGames) {
                GameRecord game = new GameRecord();
                game.id = old.id == null || old.id.isBlank() ? UUID.randomUUID().toString() : old.id;
                game.title = old.title == null ? "Игра" : old.title;
                game.createdAt = old.startedAt;
                game.updatedAt = old.completedAt > 0 ? old.completedAt : old.startedAt;
                game.startedAt = old.startedAt;
                game.finishedAt = old.completedAt;
                game.status = old.finished ? GameStatus.FINISHED : GameStatus.ACTIVE;
                game.phase = old.finished ? new GamePhase(PhaseKind.FINISHED, old.round) : legacyPhase(old.phase, old.round);
                boolean hadAssignedRoles = old.players != null && !old.players.isEmpty() && old.players.stream().allMatch(p -> p.role != null);
                game.rolesAssigned = hadAssignedRoles;
                if (old.players != null) for (LegacyPlayer player : old.players) {
                    GamePlayer migrated = new GamePlayer(player.name == null ? "Игрок" : player.name);
                    migrated.status = Boolean.FALSE.equals(player.alive) ? PlayerStatus.DEAD : PlayerStatus.ALIVE;
                    if (player.role != null) {
                        RoleDefinition snapshot = new RoleDefinition(player.role, RoleTeam.TOWN, 1, false);
                        migrated.role = new RoleSnapshot(snapshot);
                    }
                    game.players.add(migrated);
                }
                if (old.events != null) for (LegacyEvent event : old.events) {
                    GamePlayer actor = game.players.stream().filter(p -> event.actor != null && p.name.equalsIgnoreCase(event.actor)).findFirst().orElse(null);
                    GamePlayer target = game.players.stream().filter(p -> event.target != null && p.name.equalsIgnoreCase(event.target)).findFirst().orElse(null);
                    GameEvent migrated = new GameEvent(legacyPhase(event.phase, event.round), EventType.ACTION,
                            actor == null ? null : actor.id, target == null ? null : target.id,
                            event.actor == null ? "Ведущий" : event.actor,
                            event.target == null ? "" : event.target,
                            event.action == null ? "Запись из предыдущей версии" : event.action,
                            event.details == null ? "" : event.details);
                    if (event.timestamp > 0) migrated.timestamp = event.timestamp;
                    game.events.add(migrated);
                }
                save(game);
            }
            Files.move(oldFile, oldFile.resolveSibling("mafiatool-games.json.migrated.bak"), StandardCopyOption.REPLACE_EXISTING);
            recoveryMessages.add("Данные партий перенесены в новую структуру хранения.");
        } catch (Exception e) {
            recoveryMessages.add("Старый файл партий не удалось перенести; он оставлен без изменений.");
        }
    }

    private GamePhase legacyPhase(String phase, int round) {
        if (phase != null && phase.equalsIgnoreCase("ночь")) return new GamePhase(PhaseKind.NIGHT, Math.max(1, round));
        return new GamePhase(PhaseKind.DAY, Math.max(1, round));
    }

    private static final class LegacyGame {
        String id;
        String title;
        long startedAt;
        long completedAt;
        String phase;
        int round = 1;
        boolean finished;
        List<LegacyPlayer> players;
        List<LegacyEvent> events;
    }

    private static final class LegacyPlayer {
        String name;
        String role;
        Boolean alive;
    }

    private static final class LegacyEvent {
        long timestamp;
        String phase;
        int round = 1;
        String actor;
        String action;
        String target;
        String details;
    }

    public void save(GameRecord record) {
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(record.id + ".json");
            Path temp = directory.resolve(record.id + ".json.tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(record, writer);
            }
            if (Files.exists(target)) Files.copy(target, directory.resolve(record.id + ".json.bak"), StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось сохранить партию " + record.title, e);
        }
    }

    public boolean delete(String id) {
        try {
            Path path = directory.resolve(id + ".json");
            boolean existed = Files.deleteIfExists(path);
            Files.deleteIfExists(directory.resolve(id + ".json.bak"));
            Files.deleteIfExists(directory.resolve(id + ".json.tmp"));
            return existed;
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось удалить партию", e);
        }
    }

    public List<String> recoveryMessages() {
        return List.copyOf(recoveryMessages);
    }

    public String exportJson(GameRecord record) {
        return GSON.toJson(record);
    }

    public GameRecord importJson(String json) {
        GameRecord record = GSON.fromJson(json, GameRecord.class);
        if (record == null || record.id == null || record.players == null || record.events == null)
            throw new IllegalArgumentException("Файл не содержит корректную партию");
        if (record.schemaVersion != 1) throw new IllegalArgumentException("Неподдерживаемая версия данных партии");
        record.id = java.util.UUID.randomUUID().toString();
        if (record.rolePlan == null) record.rolePlan = new ArrayList<>();
        if (record.votes == null) record.votes = new ArrayList<>();
        if (record.phase == null) record.phase = new GamePhase();
        record.createdAt = System.currentTimeMillis();
        record.updatedAt = record.createdAt;
        return record;
    }
}
