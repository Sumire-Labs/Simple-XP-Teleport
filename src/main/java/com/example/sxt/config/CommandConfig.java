package com.example.sxt.config;

import com.example.sxt.cost.CostMode;
import com.example.sxt.cost.CostType;

import java.util.Collections;
import java.util.List;

/**
 * Immutable configuration for a single teleport command.
 * Missing keys fall back to the defaults documented in §3.4.
 */
public final class CommandConfig {

    private final CostMode costMode;
    private final CostType costType;
    private final int amount;
    private final double base;
    private final double perBlock;
    private final int min;
    private final int max;
    private final int crossWorldExtra;
    private final int cooldownSeconds;
    private final int warmupSeconds;
    private final boolean cancelOnMove;
    private final boolean cancelOnDamage;
    private final boolean allowInCombat;
    private final SafetyCheck safetyCheck;
    private final List<String> blacklistWorlds;
    // rtpx 用
    private final int safeSearchRadius;
    private final int minRadius;
    private final int maxRadius;
    private final int maxAttempts;
    // tpa 用
    private final int requestTimeoutSeconds;

    private CommandConfig(Builder builder) {
        this.costMode = builder.costMode;
        this.costType = builder.costType;
        this.amount = builder.amount;
        this.base = builder.base;
        this.perBlock = builder.perBlock;
        this.min = builder.min;
        this.max = builder.max;
        this.crossWorldExtra = builder.crossWorldExtra;
        this.cooldownSeconds = builder.cooldownSeconds;
        this.warmupSeconds = builder.warmupSeconds;
        this.cancelOnMove = builder.cancelOnMove;
        this.cancelOnDamage = builder.cancelOnDamage;
        this.allowInCombat = builder.allowInCombat;
        this.safetyCheck = builder.safetyCheck;
        this.blacklistWorlds = Collections.unmodifiableList(builder.blacklistWorlds);
        this.safeSearchRadius = builder.safeSearchRadius;
        this.minRadius = builder.minRadius;
        this.maxRadius = builder.maxRadius;
        this.maxAttempts = builder.maxAttempts;
        this.requestTimeoutSeconds = builder.requestTimeoutSeconds;
    }

    // ── Getters ──────────────────────────────────────────────

    public CostMode costMode()               { return costMode; }
    public CostType costType()               { return costType; }
    public int amount()                      { return amount; }
    public double base()                     { return base; }
    public double perBlock()                 { return perBlock; }
    public int min()                         { return min; }
    public int max()                         { return max; }
    public int crossWorldExtra()             { return crossWorldExtra; }
    public int cooldownSeconds()             { return cooldownSeconds; }
    public int warmupSeconds()               { return warmupSeconds; }
    public boolean cancelOnMove()            { return cancelOnMove; }
    public boolean cancelOnDamage()          { return cancelOnDamage; }
    public boolean allowInCombat()           { return allowInCombat; }
    public SafetyCheck safetyCheck()         { return safetyCheck; }
    public List<String> blacklistWorlds()    { return blacklistWorlds; }
    public int safeSearchRadius()            { return safeSearchRadius; }
    public int minRadius()                   { return minRadius; }
    public int maxRadius()                   { return maxRadius; }
    public int maxAttempts()                 { return maxAttempts; }
    public int requestTimeoutSeconds()       { return requestTimeoutSeconds; }

    // ── Builder ─────────────────────────────────────────────

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private CostMode costMode = CostMode.LEVEL;
        private CostType costType = CostType.FIXED;
        private int amount = 0;
        private double base = 0.0;
        private double perBlock = 0.0;
        private int min = 0;
        private int max = Integer.MAX_VALUE;
        private int crossWorldExtra = 0;
        private int cooldownSeconds = 0;
        private int warmupSeconds = 0;
        private boolean cancelOnMove = true;
        private boolean cancelOnDamage = true;
        private boolean allowInCombat = false;
        private SafetyCheck safetyCheck = SafetyCheck.NONE;
        private List<String> blacklistWorlds = Collections.emptyList();
        private int safeSearchRadius = 16;
        private int minRadius = 500;
        private int maxRadius = 5000;
        private int maxAttempts = 16;
        private int requestTimeoutSeconds = 60;

        Builder costMode(CostMode v)            { this.costMode = v; return this; }
        Builder costType(CostType v)            { this.costType = v; return this; }
        Builder amount(int v)                   { this.amount = v; return this; }
        Builder base(double v)                  { this.base = v; return this; }
        Builder perBlock(double v)              { this.perBlock = v; return this; }
        Builder min(int v)                      { this.min = v; return this; }
        Builder max(int v)                      { this.max = v; return this; }
        Builder crossWorldExtra(int v)          { this.crossWorldExtra = v; return this; }
        Builder cooldownSeconds(int v)          { this.cooldownSeconds = v; return this; }
        Builder warmupSeconds(int v)            { this.warmupSeconds = v; return this; }
        Builder cancelOnMove(boolean v)         { this.cancelOnMove = v; return this; }
        Builder cancelOnDamage(boolean v)       { this.cancelOnDamage = v; return this; }
        Builder allowInCombat(boolean v)        { this.allowInCombat = v; return this; }
        Builder safetyCheck(SafetyCheck v)      { this.safetyCheck = v; return this; }
        Builder blacklistWorlds(List<String> v) { this.blacklistWorlds = List.copyOf(v); return this; }
        Builder safeSearchRadius(int v)         { this.safeSearchRadius = v; return this; }
        Builder minRadius(int v)                { this.minRadius = v; return this; }
        Builder maxRadius(int v)                { this.maxRadius = v; return this; }
        Builder maxAttempts(int v)              { this.maxAttempts = v; return this; }
        Builder requestTimeoutSeconds(int v)    { this.requestTimeoutSeconds = v; return this; }

        CommandConfig build() {
            return new CommandConfig(this);
        }
    }
}
