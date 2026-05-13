package com.example.sxt.teleport;

/** Reasons a teleport may be denied (see {@link TeleportResult.Denied}). */
public enum DenyReason {
    NO_PERMISSION,
    ON_COOLDOWN,
    IN_COMBAT,
    NOT_ENOUGH_XP,
    UNSAFE_DESTINATION,
    NO_SAFE_LOCATION,
    WORLD_BLACKLISTED,
    WORLDGUARD_DENIED,
    PLAYER_NOT_FOUND
}
