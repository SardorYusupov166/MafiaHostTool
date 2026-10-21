package com.mlpgod.mafiatool.model;

public final class GamePhase {
    public PhaseKind kind = PhaseKind.PREPARATION;
    public int number;

    public GamePhase() {}

    public GamePhase(PhaseKind kind, int number) {
        this.kind = kind;
        this.number = number;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case PREPARATION -> "Подготовка";
            case NIGHT -> "Ночь " + number;
            case DAY -> "День " + number;
            case FINISHED -> "Завершена";
        };
    }
}
