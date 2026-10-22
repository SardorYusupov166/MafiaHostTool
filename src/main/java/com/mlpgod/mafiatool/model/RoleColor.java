package com.mlpgod.mafiatool.model;

/** Display color for a role, stored in the reusable role template and game snapshot. */
public enum RoleColor {
    RED("Красный", 0xFF5555),
    DARK_RED("Тёмно-красный", 0xAA0000),
    GOLD("Золотой", 0xFFAA00),
    YELLOW("Жёлтый", 0xFFFF55),
    GREEN("Зелёный", 0x55FF55),
    DARK_GREEN("Тёмно-зелёный", 0x00AA00),
    AQUA("Бирюзовый", 0x55FFFF),
    BLUE("Синий", 0x5555FF),
    DARK_BLUE("Тёмно-синий", 0x0000AA),
    PURPLE("Фиолетовый", 0xAA00AA),
    LIGHT_PURPLE("Светло-фиолетовый", 0xFF55FF),
    WHITE("Белый", 0xFFFFFF),
    GRAY("Серый", 0xAAAAAA);

    public final String label;
    public final int rgb;

    RoleColor(String label, int rgb) {
        this.label = label;
        this.rgb = rgb;
    }

    public static RoleColor defaultFor(RoleTeam team) {
        if (team == null) return GREEN;
        return switch (team) {
            case MAFIA -> RED;
            case TOWN -> GREEN;
            case NEUTRAL -> BLUE;
            case CUSTOM -> GOLD;
        };
    }
}
