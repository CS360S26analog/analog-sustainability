package com.example.klimate;

public class MimiAvatar {

    private final String id;
    private final String displayName;
    private final int riveRawResId;

    public MimiAvatar(String id, String displayName, int riveRawResId) {
        this.id = id;
        this.displayName = displayName;
        this.riveRawResId = riveRawResId;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getRiveRawResId() {
        return riveRawResId;
    }
}