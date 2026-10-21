package com.mlpgod.mafiatool.model;

import java.util.UUID;

/** Append-only event. Corrections point to the original event instead of deleting it. */
public final class GameEvent {
    public String id = UUID.randomUUID().toString();
    public long timestamp = System.currentTimeMillis();
    public GamePhase phase = new GamePhase();
    public EventType type = EventType.ACTION;
    public String actorId;
    public String targetId;
    public String actorLabel;
    public String targetLabel;
    public String description = "";
    public String result = "";
    public String originalEventId;
    public String correctionNote;

    public GameEvent() {}

    public GameEvent(GamePhase phase, EventType type, String actorId, String targetId,
                     String actorLabel, String targetLabel, String description, String result) {
        this.phase = new GamePhase(phase.kind, phase.number);
        this.type = type;
        this.actorId = actorId;
        this.targetId = targetId;
        this.actorLabel = actorLabel;
        this.targetLabel = targetLabel;
        this.description = description == null ? "" : description;
        this.result = result == null ? "" : result;
    }
}
