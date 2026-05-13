package com.example.sxt.teleport;

/**
 * Sealed result type returned by {@link TeleportService#requestTeleport}.
 *
 * <p>See §4.1 of the specification for the contract.</p>
 */
public sealed interface TeleportResult
        permits TeleportResult.Scheduled,
               TeleportResult.Denied,
               TeleportResult.Immediate {

    /** A warmup countdown is running; the teleport will proceed after {@code warmupTicks}. */
    record Scheduled(int warmupTicks) implements TeleportResult {}

    /** The teleport was rejected for the given {@link DenyReason}. */
    record Denied(DenyReason reason, long extraSeconds) implements TeleportResult {}

    /** The teleport executed immediately (no warmup required). */
    record Immediate() implements TeleportResult {}
}
