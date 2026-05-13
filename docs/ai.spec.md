# Simple XP Teleport — Agent-Ready Specification

## 0. Context & Goal

Paper サーバー(Minecraft 1.21〜1.21.11, Java 21)向けのテレポートプラグイン `SimpleXpTeleport` を実装する。プレイヤーが経験値(レベル or 総経験値ポイント)を消費して、home / warp / TPA / random TP / 座標指定 TP / back を実行できる汎用テレポートシステムを提供する。運営者が機能ごとにコスト・クールダウン・ウォームアップ・戦闘制限・安全チェックを設定可能とし、SQLite で永続化、MiniMessage で多言語(ja_JP / en_US)対応する。

---

## 1. Tech Stack & Constraints

- 言語: **Java 21** (Temurin 21、`--release 21` でコンパイル)
- 対象サーバー: **Paper 1.21.4** をビルド対象とし、`api-version: '1.21'` で 1.21〜1.21.11 をサポート対象とする <!-- inferred -->
- ビルドツール: **Gradle 8.10** (Kotlin DSL) + **Gradle Wrapper 同梱** <!-- inferred -->
- プラグイン: `io.papermc.paperweight.userdev` **1.7.7**, `xyz.jpenilla.run-paper` **2.3.1** <!-- inferred -->
- パッケージルート: `com.example.sxt`
- プラグイン名: `SimpleXpTeleport` / プラグインバージョン: `1.0.0`
- データ保存: **SQLite** (`org.xerial:sqlite-jdbc:3.46.1.0`) — 単一ファイル `plugins/SimpleXpTeleport/data.db`
- メッセージ整形: **MiniMessage** (Paper API 内包の Adventure を使用、追加依存なし)
- ソフト依存: **PlaceholderAPI 2.11.6**, **WorldGuard 7.0.11** (どちらも `compileOnly`、未導入でもプラグインは起動可)
- 対応 OS: Paper が動作する全 OS (Linux / macOS / Windows)
- 文字コード: ファイル全て **UTF-8 (BOM なし)**、改行 **LF**
- 禁止事項:
  - NMS / craftbukkit 内部パッケージへの直接アクセス禁止 (Paper API のみ使用)
  - `System.out.println` / `printStackTrace` の使用禁止 (Bukkit Logger 経由のみ)
  - リフレクションによる Paper 内部 API 呼び出し禁止
  - メインスレッドでの I/O (SQLite 含む) ブロッキング禁止 — DB 操作は `Bukkit.getScheduler().runTaskAsynchronously` で実行し、結果の Bukkit API 操作は同期スケジューラへ戻す
  - `null` を返す可能性のある public API は `Optional<T>` を返す
- ロガー名: `SimpleXpTeleport` (Bukkit が自動付与)

---

## 2. Directory Layout

```
simple-xp-teleport/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/wrapper/gradle-wrapper.properties
├── gradle/wrapper/gradle-wrapper.jar
├── README.md
├── LICENSE
└── src/
    └── main/
        ├── java/com/example/sxt/
        │   ├── SimpleXpTeleportPlugin.java
        │   ├── command/
        │   │   ├── HomeCommand.java
        │   │   ├── SetHomeCommand.java
        │   │   ├── DelHomeCommand.java
        │   │   ├── WarpCommand.java
        │   │   ├── SetWarpCommand.java
        │   │   ├── DelWarpCommand.java
        │   │   ├── TpaCommand.java
        │   │   ├── TpaHereCommand.java
        │   │   ├── TpaAcceptCommand.java
        │   │   ├── TpaDenyCommand.java
        │   │   ├── RtpCommand.java
        │   │   ├── TpPosCommand.java
        │   │   ├── BackCommand.java
        │   │   └── admin/SxtAdminCommand.java
        │   ├── teleport/
        │   │   ├── TeleportService.java
        │   │   ├── TeleportRequest.java
        │   │   ├── WarmupTask.java
        │   │   ├── CooldownManager.java
        │   │   ├── CombatTagManager.java
        │   │   ├── SafetyChecker.java
        │   │   └── RandomLocationFinder.java
        │   ├── cost/
        │   │   ├── CostCalculator.java
        │   │   ├── CostMode.java
        │   │   ├── CostType.java
        │   │   └── XpUtil.java
        │   ├── data/
        │   │   ├── DatabaseManager.java
        │   │   ├── dao/HomeDao.java
        │   │   ├── dao/WarpDao.java
        │   │   ├── dao/BackLocationDao.java
        │   │   └── model/
        │   │       ├── Home.java
        │   │       ├── Warp.java
        │   │       └── BackLocation.java
        │   ├── config/
        │   │   ├── PluginConfig.java
        │   │   └── CommandConfig.java
        │   ├── message/
        │   │   ├── MessageService.java
        │   │   └── LangLoader.java
        │   ├── permission/
        │   │   └── HomeLimitResolver.java
        │   ├── listener/
        │   │   ├── PlayerMoveListener.java
        │   │   ├── EntityDamageListener.java
        │   │   ├── PlayerDeathListener.java
        │   │   └── PlayerTeleportListener.java
        │   ├── hook/
        │   │   ├── PlaceholderApiHook.java
        │   │   └── WorldGuardHook.java
        │   └── util/
        │       ├── AuditLogger.java
        │       └── DebugLogger.java
        └── resources/
            ├── plugin.yml
            ├── config.yml
            └── lang/
                ├── ja_JP.yml
                └── en_US.yml
└── src/test/java/com/example/sxt/
    ├── cost/CostCalculatorTest.java
    ├── cost/XpUtilTest.java
    └── config/PluginConfigTest.java
```

---

## 3. Data Models / Schemas

### 3.1 SQLite スキーマ

DB ファイル: `plugins/SimpleXpTeleport/data.db`
接続文字列: `jdbc:sqlite:<dataFolder>/data.db`
PRAGMA: 接続時に `PRAGMA foreign_keys = ON;` と `PRAGMA journal_mode = WAL;` を実行する。

```sql
CREATE TABLE IF NOT EXISTS homes (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid   TEXT    NOT NULL,
    name          TEXT    NOT NULL,
    world         TEXT    NOT NULL,
    x             REAL    NOT NULL,
    y             REAL    NOT NULL,
    z             REAL    NOT NULL,
    yaw           REAL    NOT NULL,
    pitch         REAL    NOT NULL,
    created_at    INTEGER NOT NULL,  -- epoch millis
    updated_at    INTEGER NOT NULL,
    UNIQUE(player_uuid, name)
);
CREATE INDEX IF NOT EXISTS idx_homes_player ON homes(player_uuid);

CREATE TABLE IF NOT EXISTS warps (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT    NOT NULL UNIQUE,
    world         TEXT    NOT NULL,
    x             REAL    NOT NULL,
    y             REAL    NOT NULL,
    z             REAL    NOT NULL,
    yaw           REAL    NOT NULL,
    pitch         REAL    NOT NULL,
    created_by    TEXT    NOT NULL,  -- creator uuid
    created_at    INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS back_locations (
    player_uuid   TEXT    PRIMARY KEY,
    world         TEXT    NOT NULL,
    x             REAL    NOT NULL,
    y             REAL    NOT NULL,
    z             REAL    NOT NULL,
    yaw           REAL    NOT NULL,
    pitch         REAL    NOT NULL,
    saved_at      INTEGER NOT NULL,
    reason        TEXT    NOT NULL   -- 'TELEPORT' | 'DEATH'
);
```

### 3.2 Java モデル (record)

```java
public record Home(long id, UUID playerUuid, String name,
                   String world, double x, double y, double z,
                   float yaw, float pitch,
                   long createdAt, long updatedAt) {}

public record Warp(long id, String name,
                   String world, double x, double y, double z,
                   float yaw, float pitch,
                   UUID createdBy, long createdAt, long updatedAt) {}

public record BackLocation(UUID playerUuid,
                           String world, double x, double y, double z,
                           float yaw, float pitch,
                           long savedAt, BackReason reason) {}

public enum BackReason { TELEPORT, DEATH }
```

### 3.3 列挙型

```java
public enum CostMode { LEVEL, POINTS }
public enum CostType { FIXED, DISTANCE }
public enum SafetyCheck { NONE, CANCEL, FIND_SAFE }
public enum CommandKey {
    HOMEX, WARPX, TPAX, TPAHERE, RTPX, TPPOSX, BACKX
}
```

### 3.4 config.yml スキーマ

§5(原仕様)の構造をそのまま採用。`CommandConfig` は以下のフィールドを保持する:

```java
public final class CommandConfig {
    private final CostMode costMode;
    private final CostType costType;
    private final int amount;          // FIXED 用 (LEVEL 単位 or POINTS)
    private final double base;          // DISTANCE 用
    private final double perBlock;      // DISTANCE 用
    private final int min;              // DISTANCE 用 (POINTS or LEVEL の最小値)
    private final int max;              // DISTANCE 用 (上限)
    private final int crossWorldExtra;  // ワールド跨ぎ追加コスト
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
}
```

未指定キーは以下のデフォルトを採用する: `costMode=LEVEL, costType=FIXED, amount=0, base=0, perBlock=0, min=0, max=Integer.MAX_VALUE, crossWorldExtra=0, cooldownSeconds=0, warmupSeconds=0, cancelOnMove=true, cancelOnDamage=true, allowInCombat=false, safetyCheck=NONE, blacklistWorlds=[], safeSearchRadius=16, minRadius=500, maxRadius=5000, maxAttempts=16, requestTimeoutSeconds=60`. <!-- inferred -->

### 3.5 監査ログ形式

`plugins/SimpleXpTeleport/logs/audit.log` に1行1イベントの JSON Lines で書き出す。

```json
{"ts":"2026-05-13T12:34:56.789Z","event":"TELEPORT_SUCCESS","command":"homex","player":"Steve","uuid":"...","from":{"world":"world","x":1.0,"y":64.0,"z":2.0},"to":{"world":"world","x":100.0,"y":70.0,"z":200.0},"cost":{"mode":"LEVEL","amount":1},"distance":221.36}
```

`event` の取り得る値: `TELEPORT_SUCCESS`, `TELEPORT_CANCELLED`, `WARP_SET`, `WARP_DELETE`, `HOME_SET`, `HOME_DELETE`, `TPA_REQUEST`, `TPA_ACCEPT`, `TPA_DENY`, `TPA_EXPIRED`.

---

## 4. Interfaces & Contracts

### 4.1 主要サービス

```java
public final class TeleportService {
    /** Initiate a teleport with cost/cooldown/warmup/safety pipeline.
     *  Must be called from the main server thread. */
    public TeleportResult requestTeleport(Player player,
                                          Location destination,
                                          CommandKey key);
}

public sealed interface TeleportResult {
    record Scheduled(int warmupTicks) implements TeleportResult {}
    record Denied(DenyReason reason, long extraSeconds) implements TeleportResult {}
    record Immediate() implements TeleportResult {}
}

public enum DenyReason {
    NO_PERMISSION, ON_COOLDOWN, IN_COMBAT, NOT_ENOUGH_XP,
    UNSAFE_DESTINATION, NO_SAFE_LOCATION, WORLD_BLACKLISTED,
    WORLDGUARD_DENIED, PLAYER_NOT_FOUND
}
```

### 4.2 コストモジュール

```java
public final class CostCalculator {
    /** Return total cost as integer. For LEVEL mode returns levels; for POINTS mode returns total xp points. */
    public int calculate(CommandConfig cfg, Location from, Location to);
}

public final class XpUtil {
    /** Total experience points a player currently has (level + exp progress). */
    public static int getTotalExperience(Player p);
    /** Set player's total exp (levels & progress) from a points value. */
    public static void setTotalExperience(Player p, int total);
    /** Subtract `points` points. Returns true on success, false if insufficient. */
    public static boolean takePoints(Player p, int points);
    /** Subtract `levels` levels. Returns true on success, false if insufficient. */
    public static boolean takeLevels(Player p, int levels);
}
```

**距離コスト計算式 (CostType.DISTANCE)**:

```
distance = sqrt((to.x-from.x)^2 + (to.z-from.z)^2)  // y は無視
raw      = base + perBlock * distance
if (from.world != to.world) raw += crossWorldExtra
cost     = clamp(round(raw), min, max)
```

**固定コスト (CostType.FIXED)**:

```
cost = amount
if (from.world != to.world) cost += crossWorldExtra
```

### 4.3 コマンド I/O 例

`/sethomex home1` (登録成功時):
- 入力: プレイヤー `Steve` が world (10, 64, 20) に立つ。
- 出力: `[SXT] home home1 を設定しました。`
- 副作用: `homes` テーブルに 1 行 INSERT。`HOME_SET` を audit ログへ。

`/homex unknown` (存在しない home 名):
- 出力: `[SXT] home unknown は存在しません。`
- 副作用: なし。

`/sethomex home4` (上限 3 で 4 個目):
- 出力: `[SXT] home の上限に達しました(3/3)。`
- 副作用: DB 書き込みなし。

`/tpax Alex` → `Alex` 側に承認/拒否クリック付きメッセージが届く。タイムアウトは `request-timeout` 秒。

`/rtpx world` (距離 1234 ブロックの場合、`base=50, perBlock=0.1, min=100, max=500`):
- コスト = clamp(round(50 + 0.1 * 1234), 100, 500) = clamp(173, 100, 500) = **173 ポイント**

### 4.4 `/sxtadmin` サブコマンド

| サブコマンド | 引数 | 動作 |
|---|---|---|
| `reload` | なし | config.yml と lang/*.yml を再読込。実行中のウォームアップは継続。 |
| `debug` | なし | `debug` を toggle。 |
| `home <player> list` | プレイヤー名 | 対象プレイヤーの全 home を列挙。 |
| `home <player> delete <name>` | | 指定 home を削除。 |
| `home <player> tp <name>` | | 実行者を対象 home へ即時テレポート (コスト/CD 無視)。 |

権限不足時は `general.no-permission` を返す。

### 4.5 PlaceholderAPI プレースホルダ

PlaceholderAPI 導入時に `PlaceholderApiHook` で次を登録:

| プレースホルダ | 戻り値 |
|---|---|
| `%sxt_home_count%` | 当該プレイヤーの home 件数 |
| `%sxt_home_max%` | 当該プレイヤーの home 上限 |
| `%sxt_cooldown_<command>%` | 残 CD 秒 (整数、0 以上) |
| `%sxt_in_combat%` | `true` / `false` |

未知キーは空文字を返す。

### 4.6 WorldGuard カスタムフラグ

`StateFlag` を 1 つ登録:
- 名前: `sxt-teleport`
- デフォルト: `ALLOW`
- 効果: `DENY` のリージョンに着地点が含まれる場合 `WORLDGUARD_DENIED` で拒否。

WorldGuard 未導入の場合はフックを完全にスキップする。

---

## 5. Implementation Steps

### Step 1 — Gradle プロジェクト骨格の作成

**Goal**: ビルドが通る空のプラグインを作成する。

**Files to touch**:
- `[CREATE] settings.gradle.kts`
- `[CREATE] build.gradle.kts`
- `[CREATE] gradle.properties`
- `[CREATE] gradle/wrapper/gradle-wrapper.properties` (Gradle 8.10)
- `[CREATE] src/main/resources/plugin.yml` (空に近い最小内容)
- `[CREATE] src/main/java/com/example/sxt/SimpleXpTeleportPlugin.java` (空の `onEnable`/`onDisable`)

`build.gradle.kts`:

```kotlin
plugins {
    java
    id("io.papermc.paperweight.userdev") version "1.7.7"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "com.example.sxt"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.11")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

tasks {
    test { useJUnitPlatform() }
    runServer { minecraftVersion("1.21.4") }
    assemble { dependsOn(reobfJar) }
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}
```

**Acceptance**: `./gradlew build` がエラーなく完了する。`build/libs/simple-xp-teleport-1.0.0.jar` が生成される。

---

### Step 2 — plugin.yml と全コマンド/権限の登録

**Goal**: コマンド/権限を `plugin.yml` に宣言。各コマンドの `CommandExecutor` は空実装で OK。

**Files to touch**:
- `[EDIT] src/main/resources/plugin.yml`
- `[CREATE]` §2 の `command/*.java` 全ファイル(空骨格、`onCommand` は `return true;`)

`plugin.yml` の `commands:` と `permissions:` は §3(原仕様)と §4(原仕様)の表に従い全項目を列挙する。`softdepend: [PlaceholderAPI, WorldGuard]` を明記。

**Acceptance**: Paper サーバーへ jar を投入してプラグインが `Enabled` ログを出す。各コマンドが `/help` で見える。

---

### Step 3 — Config 読み込み

**Goal**: `config.yml` を §5(原仕様)通りにロードし、`PluginConfig` と `CommandConfig` を保持する。

**Files to touch**:
- `[CREATE] src/main/resources/config.yml` (§5 をそのまま使用)
- `[CREATE] src/main/java/com/example/sxt/config/PluginConfig.java`
- `[CREATE] src/main/java/com/example/sxt/config/CommandConfig.java`

`PluginConfig` は以下を保持: `language`, `debug`, `storageFile`, `defaultMaxHomes`, `combatTagPvpDuration`, `globalBlacklistWorlds`, `effects(enabled,Particle,Sound x4)`, `auditLogEnabled`, `auditLogFile`, `Map<CommandKey, CommandConfig> commands`.

不正値・欠損キーは §3.4 のデフォルトでフォールバックし、`getLogger().warning` で警告ログを出す。

**Acceptance**: 起動時に `getLogger().info("Config loaded: " + commands.size() + " command configs")` が出力される。JUnit: `PluginConfigTest` で `commands.homex.cost.amount=1` がロードされることを検証。

---

### Step 4 — メッセージサービス (MiniMessage + lang)

**Goal**: `MessageService.send(CommandSender, "home.set", Map.of("home","mybase"))` で MiniMessage を解決して送信できる。

**Files to touch**:
- `[CREATE] src/main/resources/lang/ja_JP.yml` (§6 と同一)
- `[CREATE] src/main/resources/lang/en_US.yml` (同じキー構造で英訳)
- `[CREATE] src/main/java/com/example/sxt/message/LangLoader.java`
- `[CREATE] src/main/java/com/example/sxt/message/MessageService.java`

仕様:
- `LangLoader` は `data folder/lang/<language>.yml` を優先し、無ければ jar 内同名を `saveResource` で展開してから読み込む。
- `MessageService.format(key, placeholders)` は MiniMessage に `<key>` 形式の `TagResolver.resolver` を追加。先頭に `prefix` を自動結合 (キーが `prefix` 自体の場合を除く)。
- 未知キーは `<red>Missing message: <key></red>` を返し、コンソールに `warning` を出す。

`en_US.yml` の翻訳例:

```yaml
prefix: "<gray>[<aqua>SXT</aqua>]</gray> "
general:
  no-permission: "<prefix><red>You don't have permission.</red>"
  player-only: "<prefix><red>This command is for players only.</red>"
  player-not-found: "<prefix><red>Player <yellow><target></yellow> not found.</red>"
  reload-success: "<prefix><green>Configuration reloaded.</green>"
  debug-on: "<prefix><yellow>Debug mode enabled.</yellow>"
  debug-off: "<prefix><yellow>Debug mode disabled.</yellow>"
# ... (全キーを ja_JP.yml と同構造で英訳)
```

**Acceptance**: `/sxtadmin reload` 実行で `general.reload-success` が日本語/英語どちらでも正しく整形されチャットに表示される。

---

### Step 5 — SQLite 接続と DAO 実装

**Goal**: `homes` / `warps` / `back_locations` の CRUD を非同期で提供。

**Files to touch**:
- `[CREATE] src/main/java/com/example/sxt/data/DatabaseManager.java`
- `[CREATE] src/main/java/com/example/sxt/data/dao/HomeDao.java`
- `[CREATE] src/main/java/com/example/sxt/data/dao/WarpDao.java`
- `[CREATE] src/main/java/com/example/sxt/data/dao/BackLocationDao.java`
- `[CREATE] src/main/java/com/example/sxt/data/model/*.java`

要件:
- `DatabaseManager.connect()` はプラグイン有効化時に同期で 1 度だけ実行し、上記 PRAGMA とテーブル DDL を流す。
- 各 DAO の public メソッドは `CompletableFuture<T>` を返す。内部実装は `Bukkit.getScheduler().runTaskAsynchronously` で SQL を実行。
- `HomeDao` の必須メソッド: `save(Home)`, `delete(UUID, String)`, `findOne(UUID, String)`, `listByPlayer(UUID)`, `countByPlayer(UUID)`.
- `WarpDao`: `save(Warp)`, `delete(String)`, `findOne(String)`, `listAll()`.
- `BackLocationDao`: `upsert(BackLocation)`, `find(UUID)`.

例外時は `CompletableFuture.failedFuture(new SqlException(...))` を返し、呼び出し側で `.exceptionally` ログ出力 + ユーザー向けエラーメッセージ。

**Acceptance**: 統合テスト相当として、サーバー起動後 `/sethomex` → 再起動 → `/homex` で復元できる。

---

### Step 6 — Cost 計算と XP ユーティリティ

**Goal**: §4.2 の式どおりに `CostCalculator` を実装し、`XpUtil` で正確にレベル/ポイントを増減できる。

**Files to touch**:
- `[CREATE] src/main/java/com/example/sxt/cost/CostMode.java`
- `[CREATE] src/main/java/com/example/sxt/cost/CostType.java`
- `[CREATE] src/main/java/com/example/sxt/cost/CostCalculator.java`
- `[CREATE] src/main/java/com/example/sxt/cost/XpUtil.java`

`XpUtil` の `getTotalExperience` は Minecraft の式に従う:
- レベル `L` までの総 XP:
  - `L<=16`: `L^2 + 6L`
  - `16<L<=31`: `2.5L^2 - 40.5L + 360`
  - `L>31`: `4.5L^2 - 162.5L + 2220`
- 現在総 XP = (上記でレベル L までの合計) + `floor(p.getExp() * p.getExpToLevel())`

`takePoints(player, n)` は `setTotalExperience(player, current - n)` で再計算する。`setTotalExperience` は `setLevel(0); setExp(0); giveExp(total)` を使用してはならない (Paper の挙動差異回避のため、手動で `level` と `exp` を再計算して `setLevel` / `setExp` する)。

**Acceptance**: `CostCalculatorTest` と `XpUtilTest` が green。
- ケース: `from=(0,0,0,world), to=(300,0,400,world), base=10, perBlock=0.05, min=20, max=300, crossWorldExtra=200` → `distance=500, raw=10+25=35, clamp(35,20,300)=35`.
- ケース: 同上で `to.world="nether"` → `raw=235, cost=235`.

---

### Step 7 — Cooldown / CombatTag / Safety / RandomLocation

**Goal**: 4 つのマネージャを実装。

**Files to touch**:
- `[CREATE] src/main/java/com/example/sxt/teleport/CooldownManager.java`
- `[CREATE] src/main/java/com/example/sxt/teleport/CombatTagManager.java`
- `[CREATE] src/main/java/com/example/sxt/teleport/SafetyChecker.java`
- `[CREATE] src/main/java/com/example/sxt/teleport/RandomLocationFinder.java`

要件:
- `CooldownManager`: `Map<UUID, Map<CommandKey, Long>>` で各最終実行時刻 epoch ms を保持。`remainingSeconds(player, key, configSec)` を提供。bypass パーミッション `sxt.bypass.cooldown.<cmd>` or `sxt.bypass.cooldown.*` を持つ場合は 0 を返す。
- `CombatTagManager`: `EntityDamageByEntityEvent` で player vs player の場合のみ両者にタグ付与 (epoch ms)。`isInCombat(player)` は `now < taggedAt + pvpDuration*1000` を返す。
- `SafetyChecker`:
  - `isSafe(Location)`: 足元ブロックが `solid` かつ頭・体位置が非衝突かつ非液体・非火・非マグマ・非サボテン。Y が `world.getMinHeight()..world.getMaxHeight()-2` の範囲内。
  - `findSafe(Location center, int radius)`: 中心から螺旋状に水平探索し、各 (x,z) 毎に `world.getHighestBlockYAt` + 1 を候補 Y にして `isSafe` を確認。最大 `radius^2` 試行で見つからなければ `Optional.empty()`。
- `RandomLocationFinder.find(World, CommandConfig)`:
  - 中心は `world.getSpawnLocation()`。
  - 半径を `min..max` でランダム、角度を `0..2π` でランダム。`maxAttempts` 回試行。
  - 各候補で `SafetyChecker.findSafe(loc, safeSearchRadius)` を試し、最初に成功したものを返す。全失敗時は `Optional.empty()`。

**Acceptance**: `/rtpx world` を実行すると常に陸地に着地し、空中/水中/溶岩に着地しない。CD 中の再実行で `cooldown.active` が表示され、`<cooldown>` に正しい残秒が入る。

---

### Step 8 — TeleportService(統合パイプライン)

**Goal**: テレポート前の全チェックとウォームアップ・コスト消費・実行・back 記録を一元化。

**Files to touch**:
- `[CREATE] src/main/java/com/example/sxt/teleport/TeleportRequest.java`
- `[CREATE] src/main/java/com/example/sxt/teleport/WarmupTask.java`
- `[CREATE] src/main/java/com/example/sxt/teleport/TeleportService.java`
- `[CREATE] src/main/java/com/example/sxt/listener/PlayerMoveListener.java`
- `[CREATE] src/main/java/com/example/sxt/listener/EntityDamageListener.java`
- `[CREATE] src/main/java/com/example/sxt/listener/PlayerTeleportListener.java`
- `[CREATE] src/main/java/com/example/sxt/listener/PlayerDeathListener.java`

パイプライン (順序固定):

1. `key` に対応する `sxt.use.<cmd>` 権限を持つか? → no なら `general.no-permission`。
2. 現在ワールド or 着地ワールドが `globalBlacklist` or `cfg.blacklistWorlds` か? → yes なら `world.blacklisted`。
3. WorldGuard 導入時、着地点が `sxt-teleport=DENY` リージョンか? → yes なら `world.worldguard-denied`。
4. `allow-in-combat=false` かつ `CombatTagManager.isInCombat(p)` で `sxt.bypass.combat.*` を持たない → `combat.blocked`。`<cooldown>` には残戦闘秒数。
5. CD 中で bypass 無し → `cooldown.active`。
6. `cfg.safetyCheck` に応じて
   - `NONE`: 何もしない
   - `CANCEL`: `isSafe(dest)==false` なら `safety.unsafe-cancelled`
   - `FIND_SAFE`: `findSafe(dest, safeSearchRadius)` で更新。見つからなければ `safety.no-safe-location`
7. コスト計算。bypass `sxt.bypass.cost.*` で 0 に。コスト > 残量なら `cost.not-enough-level` / `cost.not-enough-points`。
8. `warmup>0` かつ bypass 無し → `WarmupTask` 開始 (毎 tick `Math.hypot(dx,dz) > 0.1` で移動検知)。`cancel-on-damage` 時は `EntityDamageEvent` で中断。完了時に下記 9〜11 を実行。
9. コスト消費 (`XpUtil`)。失敗時 `cost.not-enough-*`。
10. **back 用に現在地を保存** (`BackLocationDao.upsert(reason=TELEPORT)`)。`/backx` 自身の場合は保存しない。
11. `Player.teleportAsync(dest, COMMAND)` を呼ぶ。完了後に CD 開始、`*.teleporting` メッセージ送信、`audit-log` 出力、効果音・パーティクル再生。

`PlayerDeathListener` は `PlayerDeathEvent` で死亡位置を `BackLocationDao.upsert(reason=DEATH)` する。

**Acceptance**: 各拒否条件で正しいメッセージが返り、ウォームアップ中の移動でキャンセルされ、成功時に audit ログ 1 行追記される。

---

### Step 9 — Home / Warp 系コマンド実装

**Goal**: §3(原仕様)の home / warp 関連コマンドを `TeleportService` 経由で完成させる。

**Files to touch**: `command/HomeCommand.java`, `SetHomeCommand.java`, `DelHomeCommand.java`, `WarpCommand.java`, `SetWarpCommand.java`, `DelWarpCommand.java`, `permission/HomeLimitResolver.java`.

`HomeLimitResolver.resolve(Player p)`:
- `sxt.homes.unlimited` を持つ → `Integer.MAX_VALUE`
- それ以外、付与されている `sxt.homes.max.<n>` 全ての `n` の最大値を返す
- どれも無ければ `config.home.default-max-count`

`/sethomex [name]`: 名前未指定時は `"home"`。バリデーション: `^[a-zA-Z0-9_\-]{1,32}$`、満たさない場合 `general.player-only` ではなく独自エラー `home.invalid-name` (lang に追記)。

`/delhomex [name]`: 存在しなければ `home.not-found`。

**Acceptance**: 連続して `/sethomex a`, `/sethomex b`, `/sethomex c`, `/sethomex d` を実行し、上限 3 なら 4 回目で `home.limit-reached` が出る。`/homex a` でテレポート、ウォームアップ・コスト消費が想定どおり。

---

### Step 10 — TPA 系コマンド

**Goal**: `/tpax`, `/tpahere`, `/tpacceptx`, `/tpdenyx` を実装。

**Files to touch**: `command/Tpa*.java`, `teleport/TeleportRequest.java` を拡張(`Map<UUID, Pending>` をプラグインフィールドで保持)。

仕様:
- `Pending` = `{ requesterUuid, targetUuid, type(TPA|TPAHERE), createdAtMs }`
- 受信者ごとに最新 1 件のみ保持 (新しいリクエストで上書き)。
- `request-timeout` 秒経過で自動破棄し `tpa.expired` を双方に送信。
- 承認時、`type=TPA` なら requester を target の位置へ、`type=TPAHERE` なら target を requester の位置へ送る。
- コスト・CD は **承認時** に **requester** から徴収。
- 同一 UUID 同士 / オフラインプレイヤー指定は `general.player-not-found`。

**Acceptance**: 2 プレイヤーで `/tpax B` → クリック承認 → A が B の位置へ移動、A のレベル/XP が消費される。タイムアウト経路も動作。

---

### Step 11 — RTP / TPPOS / Back コマンド

**Goal**: 残り 3 コマンドを実装。

**Files to touch**: `command/RtpCommand.java`, `TpPosCommand.java`, `BackCommand.java`.

- `/rtpx [world]`: world 省略時はプレイヤーの現在ワールド。`RandomLocationFinder` で位置決定後 `TeleportService.requestTeleport`。`rtp.searching` を即座に送信し、見つからなければ `safety.no-safe-location`。
- `/tpposx <x> <y> <z> [world]`: 数値検証、範囲 `world.getMinHeight() <= y < world.getMaxHeight()`。
- `/backx`: `BackLocationDao.find(uuid)` で取得、無ければ `back.no-previous`。

**Acceptance**: `/rtpx` が `min-radius..max-radius` の円環内に着地し、空中/液体/危険ブロックを避ける。`/backx` で直前位置に戻る。

---

### Step 12 — /sxtadmin と Listener 仕上げ

**Goal**: 管理コマンドと残るリスナーを完成。

**Files to touch**: `command/admin/SxtAdminCommand.java`, `listener/PlayerTeleportListener.java`.

- `reload`: `PluginConfig.reload()` と `LangLoader.reload()` を呼ぶ。
- `debug`: `pluginConfig.toggleDebug()`。
- `home <player> ...`: `sxt.manage.home.others` を要求。
- `PlayerTeleportListener`: 自プラグイン以外の `PlayerTeleportEvent` (cause が `ENDER_PEARL`, `CHORUS_FRUIT`, `PLUGIN` の `PLUGIN` 以外) でも back 位置を更新する選択肢を提供。`config.yml` に `back.record-non-plugin-teleports: false` (デフォルト false) <!-- inferred -->。

**Acceptance**: `/sxtadmin reload` 後に config を変更したコストが即時反映。

---

### Step 13 — PlaceholderAPI / WorldGuard フック

**Goal**: それぞれの導入時のみ機能を有効化。未導入なら警告なくスキップ。

**Files to touch**: `hook/PlaceholderApiHook.java`, `hook/WorldGuardHook.java`.

- `onEnable` で `Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null` の場合のみ `new PlaceholderApiHook(this).register()` を呼ぶ。
- WorldGuard のフラグ登録は `onLoad` フェーズで `WorldGuard.getInstance().getFlagRegistry().register(...)` を行う必要があるため、`SimpleXpTeleportPlugin.onLoad()` から `WorldGuardHook.tryRegisterFlag()` を呼ぶ。

**Acceptance**: PlaceholderAPI 未導入の Paper サーバーでもエラーなく起動。導入時は `%sxt_home_count%` がチャットで解決される (`/papi parse me %sxt_home_count%`)。

---

### Step 14 — AuditLogger / DebugLogger

**Goal**: 監査ログとデバッグログを実装。

**Files to touch**: `util/AuditLogger.java`, `util/DebugLogger.java`.

- `AuditLogger.log(String event, Map<String,Object> fields)` は §3.5 の JSON 行を `plugins/SimpleXpTeleport/logs/audit.log` に追記。書き込みは非同期 (`runTaskAsynchronously`)、`StandardOpenOption.CREATE, APPEND` で `Files.write`。
- `DebugLogger.debug(String msg)` は `pluginConfig.isDebug()` 時のみ `getLogger().info("[DEBUG] " + msg)`。

**Acceptance**: テレポート 1 回ごとに 1 行追記され、JSON として valid。

---

### Step 15 — テスト整備 & 仕上げ

**Goal**: §6 のテストを通し、`./gradlew build` を成功させる。

**Files to touch**: `src/test/java/...`, `README.md`.

- `CostCalculatorTest`: §4.2 の 4 シナリオを検証。
- `XpUtilTest`: Bukkit `Player` をモック (Mockito) し、レベル/XP 計算を検証。
- `PluginConfigTest`: テスト用 `config.yml` をクラスパスから読み、フィールドが期待値であることを検証。

**Acceptance**: `./gradlew build` で test/compile/jar 全て成功。

---

## 6. Test Plan

### 6.1 単体テスト (JUnit 5)

| ID | Given | When | Then |
|---|---|---|---|
| U-1 | `CommandConfig{type=FIXED, amount=1, crossWorldExtra=2}` | `calculate(from=world, to=world)` | `1` |
| U-2 | 同上 | `calculate(from=world, to=nether)` | `3` |
| U-3 | `{type=DISTANCE, base=10, perBlock=0.05, min=20, max=300}` from(0,0,0,w)→to(300,0,400,w) | `calculate` | `35` |
| U-4 | 同上 + `crossWorldExtra=200`, to.world=nether | `calculate` | `235` |
| U-5 | プレイヤー level=5, exp=0.5, expToLevel=10 | `getTotalExperience` | `5^2 + 6*5 + 5 = 60` |
| U-6 | プレイヤー total=100 | `setTotalExperience(50)` → `getTotalExperience` | `50` |
| U-7 | config に `homex.cost.amount: 1` | `PluginConfig.load` | `commands[HOMEX].amount == 1` |

### 6.2 結合テスト (手動 / `runServer` 上)

| ID | Given | When | Then |
|---|---|---|---|
| I-1 | プレイヤー level=10, homex cost=3 | `/sethomex base` → 100 ブロック移動 → `/homex base` | level=7, ベース位置へ移動 |
| I-2 | プレイヤー level=0, homex cost=1 | `/homex base` | `cost.not-enough-level` 表示、テレポートしない |
| I-3 | rtpx 設定 (min=500,max=5000) | `/rtpx world` | 着地地点が水平距離 500〜5000 内かつ陸地 |
| I-4 | A,B 別ワールド | A: `/tpax B`, B: `/tpacceptx` | A が B のワールド/位置に出現、A のコスト消費 |
| I-5 | warmup=3, cancel-on-move=true | `/homex base` → 0.5 秒後に 1 ブロック移動 | `warmup.cancelled-move`、テレポートなし |
| I-6 | PvP ダメージ直後 (combat tag 中) | `/homex base` | `combat.blocked` (allow-in-combat=false の場合) |
| I-7 | home 上限 3、現在 3 個 | `/sethomex fourth` | `home.limit-reached(3/3)` |
| I-8 | WorldGuard でリージョン `sxt-teleport=DENY` | 着地点がそのリージョン | `world.worldguard-denied` |
| I-9 | safety-check=FIND_SAFE、目的地が空中 | `/rtpx world` | 近傍の安全地点に着地 |
| I-10 | プレイヤーが死亡後 | `/backx` | 死亡地点に戻り `back.teleporting` 表示 |
| I-11 | `/sxtadmin reload` | config の `homex.cooldown` を 30 に変更後 | 次回 `/homex` から CD=30 が適用される |

### 6.3 E2E (Smoke)

- Paper 1.21.4 サーバーで `./gradlew runServer` を起動、OP プレイヤーで上記 I-1〜I-11 を順に確認できる。
- PlaceholderAPI 未導入でもプラグインが `Enabled` ログを出す。

---

## 7. Definition of Done

- [ ] `./gradlew build` が警告以外エラーなく完了する。
- [ ] `build/libs/simple-xp-teleport-1.0.0.jar` が生成される。
- [ ] `./gradlew test` で全 JUnit テストが green。
- [ ] `./gradlew runServer` でサーバー起動、`Enabled` ログが出る。
- [ ] §3 の全コマンドが `/help` から見え、それぞれが §6 の I-1〜I-11 を満たす。
- [ ] `plugin.yml` に §4(原仕様)の全権限が宣言されている。
- [ ] `config.yml` をプラグインフォルダから手動編集 → `/sxtadmin reload` で反映される。
- [ ] `lang/ja_JP.yml` と `lang/en_US.yml` が同じキー集合を持ち、`language` 設定で切替可能。
- [ ] PlaceholderAPI / WorldGuard を未導入の環境でも例外なく起動する。
- [ ] SQLite ファイル `data.db` がプラグインフォルダに作成され、再起動後も home/warp/back が保持される。
- [ ] 監査ログ `logs/audit.log` に成功テレポート毎に JSON 行が追記される。
- [ ] `System.out.println` / `e.printStackTrace()` が grep でヒットしない。
- [ ] 全 `.java` ファイルが UTF-8/LF。

---

## 8. Out of Scope

- 経済プラグイン (Vault / EssentialsX Economy) との通貨連携。
- ベッド / リスポーンアンカー設定の上書き。
- GUI ベースの home/warp 選択メニュー。
- Bungee / Velocity 経由のサーバー間テレポート。
- Folia 対応 (Paper のみ対象)。
- 1.21.x 未満および 1.22 以降のサポート。
- bStats などのメトリクス送信。
- 自動アップデートチェッカー。
