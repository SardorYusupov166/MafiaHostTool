package com.mlpgod.mafiatool.model;

import java.util.UUID;

public final class VoteRecord {
    public String id = UUID.randomUUID().toString();
    public String voterId;
    public String targetId;
    public int dayNumber;
    public long timestamp = System.currentTimeMillis();
}
