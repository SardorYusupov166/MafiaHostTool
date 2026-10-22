package com.mlpgod.mafiatool.model;

import java.util.UUID;

/** A user-defined rule that reacts to a journal event. */
public final class ActionMacro {
    public String id = UUID.randomUUID().toString();
    public String name = "Новый макрос";
    public EventType trigger = EventType.KILL_ATTEMPT;
    /** Optional case-insensitive substring matched against event description and result. */
    public String condition = "";
    public MacroEffect effect = MacroEffect.ADD_LOG_NOTE;
    /** Template used by ADD_LOG_NOTE and as the death reason for MARK_TARGET_DEAD. */
    public String textTemplate = "{actor} → {target}: {result}";
    public boolean enabled = true;

    public ActionMacro copy() {
        ActionMacro copy = new ActionMacro();
        copy.id = id;
        copy.name = name;
        copy.trigger = trigger;
        copy.condition = condition;
        copy.effect = effect;
        copy.textTemplate = textTemplate;
        copy.enabled = enabled;
        return copy;
    }
}
