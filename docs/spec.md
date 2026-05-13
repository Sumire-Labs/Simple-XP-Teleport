# Simple XP Teleport プラグイン仕様書

## 1. 概要

| 項目 | 内容 |
|------|------|
| プロジェクト名 | Simple XP Teleport |
| 対応 Minecraft | 1.21 ~ 1.21.11 |
| 対応サーバー | Paper |
| 必要 Java | Java 21 |
| ビルドツール | Gradle (Kotlin DSL) + paperweight-userdev |
| パッケージ | `com.example.sxt` |
| データ保存 | SQLite(単一DBファイル) |
| ローカライズ | MiniMessage + ja_JP / en_US 同梱 |
| ソフト依存 | PlaceholderAPI, WorldGuard |

経験値(レベル or ポイント)を消費してテレポートする、Paper 向けの汎用テレポートプラグイン。home / warp / TPA / ランダムTP / 座標指定TP / back を提供し、距離連動コスト・クールダウン・ウォームアップ(詠唱)・PvP戦闘制限・安全チェック・複数home対応など、運営者がきめ細かく調整できる設計。

## 2. 機能一覧

home の設定・呼び出し・削除(複数home対応、権限による上限制御)、管理者設定の warp、プレイヤー間TPリクエスト(`/tpax`, `/tpahere`)とその承認・拒否、ランダムテレポート(`/rtpx`)、座標指定テレポート(`/tpposx`)、直前位置への帰還(`/backx`)、機能ごとに設定可能な経験値消費(LEVEL/POINTS、固定/距離連動)、機能ごとのクールダウン・ウォームアップ・戦闘制限・安全チェック・ワールド間TP制御、MiniMessage 形式のローカライズメッセージ、PlaceholderAPI 連携、WorldGuard 連携(カスタムフラグ)、デバッグモードと監査ログ。

## 3. コマンド仕様

| コマンド | 引数 | 説明 | デフォルト権限 |
|---------|------|------|---------------|
| `/homex` | `[name]` | 指定home(省略時 "home")へTP | `true` |
| `/sethomex` | `[name]` | 現在位置をhomeとして登録 | `true` |
| `/delhomex` | `[name]` | 指定homeを削除 | `true` |
| `/warpx` | `<name>` | 指定warpへTP | `true` |
| `/setwarpx` | `<name>` | 現在位置をwarpとして登録(管理者) | `op` |
| `/delwarpx` | `<name>` | 指定warpを削除(管理者) | `op` |
| `/tpax` | `<player>` | 対象プレイヤーへのTPリクエスト送信 | `true` |
| `/tpahere` | `<player>` | 対象プレイヤーを自分の所へ呼ぶリクエスト | `true` |
| `/tpacceptx` | - | 直近のTPリクエストを承認 | `true` |
| `/tpdenyx` | - | 直近のTPリクエストを拒否 | `true` |
| `/rtpx` | `[world]` | ランダム座標へTP | `true` |
| `/tpposx` | `<x> <y> <z> [world]` | 座標指定TP | `op` |
| `/backx` | - | 直前のTP前位置 / 死亡地点へ戻る | `true` |
| `/sxtadmin` | `<reload\|debug\|...>` | 管理サブコマンド | `op` |

`/sxtadmin` サブコマンド: `reload`(設定/言語ファイル再読込)、`debug`(デバッグモードON/OFF切替)、`home <player> <list\|delete <name>\|tp <name>>`(他人のhome操作、`sxt.manage.home.others` 必要)。

## 4. 権限一覧

| 権限ノード | 説明 | デフォルト |
|-----------|------|-----------|
| `sxt.use.homex` | `/homex` 使用 | true |
| `sxt.use.sethomex` | `/sethomex` 使用 | true |
| `sxt.use.delhomex` | `/delhomex` 使用 | true |
| `sxt.use.warpx` | `/warpx` 使用 | true |
| `sxt.use.tpax` | `/tpax` 使用 | true |
| `sxt.use.tpahere` | `/tpahere` 使用 | true |
| `sxt.use.tpacceptx` | `/tpacceptx` 使用 | true |
| `sxt.use.tpdenyx` | `/tpdenyx` 使用 | true |
| `sxt.use.rtpx` | `/rtpx` 使用 | true |
| `sxt.use.backx` | `/backx` 使用 | true |
| `sxt.use.tpposx` | `/tpposx` 使用 | op |
| `sxt.use.*` | 全使用権限 | op |
| `sxt.manage.warp` | warp の作成/削除 | op |
| `sxt.manage.home.others` | 他人の home の閲覧/操作 | op |
| `sxt.homes.max.<n>` | home 所持上限を `<n>` に拡張 | false |
| `sxt.homes.unlimited` | home 無制限 | op |
| `sxt.bypass.cooldown.<cmd>` | 指定コマンドのCDをバイパス | op |
| `sxt.bypass.cooldown.*` | 全コマンドCDバイパス | op |
| `sxt.bypass.warmup.<cmd>` | 指定コマンドのウォームアップをバイパス | op |
| `sxt.bypass.warmup.*` | 全コマンドウォームアップバイパス | op |
| `sxt.bypass.combat.<cmd>` | 戦闘中制限のバイパス | op |
| `sxt.bypass.combat.*` | 全戦闘中制限バイパス | op |
| `sxt.bypass.cost.<cmd>` | XP消費のバイパス | op |
| `sxt.bypass.cost.*` | 全XP消費バイパス | op |
| `sxt.admin.reload` | 設定再読込 | op |
| `sxt.admin.debug` | デバッグ切替 | op |
| `sxt.admin.*` | 全管理権限 | op |
| `sxt.*` | 全権限 | op |

## 5. config.yml サンプル

```yaml
# Simple XP Teleport - config.yml
# 各コマンドの cost / cooldown / warmup / combat / safety を機能ごとに設定可能

language: ja_JP
debug: false

storage:
  type: SQLITE
  sqlite:
    file: "data.db"

home:
  default-max-count: 3   # sxt.homes.max.<n> / sxt.homes.unlimited で上書き可

# 戦闘タグ設定(PvPダメージで付与)
combat-tag:
  pvp-duration: 15       # 秒

# ワールド制御
worlds:
  global-blacklist: []   # 全機能で禁止するワールド

# パーティクル/サウンド演出(ウォームアップ中)
effects:
  enabled: true
  particle: ENCHANTMENT_TABLE
  warmup-sound: BLOCK_PORTAL_AMBIENT
  start-sound: BLOCK_BEACON_ACTIVATE
  success-sound: ENTITY_ENDERMAN_TELEPORT
  cancel-sound: ENTITY_VILLAGER_NO

# 監査ログ
audit-log:
  enabled: true
  file: "logs/audit.log"

# ────────────────────────────────────────────
# 各コマンドの詳細設定
# cost.mode:   LEVEL  | POINTS
# cost.type:   FIXED  | DISTANCE
# safety-check: NONE  | CANCEL | FIND_SAFE
# ────────────────────────────────────────────

commands:
  homex:
    cost:
      mode: LEVEL
      type: FIXED
      amount: 1
      cross-world-extra: 2
    cooldown: 3            # 秒
    warmup: 3              # 秒
    cancel-on-move: true
    cancel-on-damage: true
    allow-in-combat: false
    safety-check: NONE
    blacklist-worlds: []

  warpx:
    cost:
      mode: POINTS
      type: DISTANCE
      base: 10
      per-block: 0.05
      min: 20
      max: 300
      cross-world-extra: 200
    cooldown: 5
    warmup: 3
    cancel-on-move: true
    cancel-on-damage: true
    allow-in-combat: false
    safety-check: CANCEL
    blacklist-worlds: []

  tpax:
    cost:
      mode: LEVEL
      type: FIXED
      amount: 3
      cross-world-extra: 2
    cooldown: 10
    warmup: 3
    cancel-on-move: true
    cancel-on-damage: true
    allow-in-combat: false
    safety-check: CANCEL
    request-timeout: 60    # 秒
    blacklist-worlds: []

  tpahere:
    cost:
      mode: LEVEL
      type: FIXED
      amount: 3
      cross-world-extra: 2
    cooldown: 10
    warmup: 3
    cancel-on-move: true
    cancel-on-damage: true
    allow-in-combat: false
    safety-check: CANCEL
    request-timeout: 60
    blacklist-worlds: []

  rtpx:
    cost:
      mode: POINTS
      type: DISTANCE
      base: 50
      per-block: 0.1
      min: 100
      max: 500
      cross-world-extra: 200
    cooldown: 60
    warmup: 5
    cancel-on-move: true
    cancel-on-damage: true
    allow-in-combat: false
    safety-check: FIND_SAFE
    safe-search-radius: 16
    # ランダム範囲(中心: ワールドスポーン)
    min-radius: 500
    max-radius: 5000
    max-attempts: 16
    blacklist-worlds: ["world_nether", "world_the_end"]

  tpposx:
    cost:
      mode: POINTS
      type: DISTANCE
      base: 20
      per-block: 0.1
      min: 50
      max: 500
      cross-world-extra: 200
    cooldown: 30
    warmup: 5
    cancel-on-move: true
    cancel-on-damage: true
    allow-in-combat: false
    safety-check: CANCEL
    blacklist-worlds: []

  backx:
    cost:
      mode: LEVEL
      type: FIXED
      amount: 1
      cross-world-extra: 2
    cooldown: 5
    warmup: 0
    cancel-on-move: false
    cancel-on-damage: false
    allow-in-combat: true   # 死亡直後の復帰用に許可
    safety-check: CANCEL
    blacklist-worlds: []
```

## 6. メッセージファイル例(lang/ja_JP.yml)

```yaml
# Simple XP Teleport - 日本語メッセージ(MiniMessage 形式)
# プレースホルダ: <player>, <target>, <cost>, <cooldown>, <warmup>,
#                <distance>, <world>, <home>, <warp>, <x>, <y>, <z>, <count>, <max>

prefix: "<gray>[<aqua>SXT</aqua>]</gray> "

general:
  no-permission: "<prefix><red>権限がありません。</red>"
  player-only: "<prefix><red>このコマンドはプレイヤーのみ実行できます。</red>"
  player-not-found: "<prefix><red>プレイヤー <yellow><target></yellow> が見つかりません。</red>"
  reload-success: "<prefix><green>設定をリロードしました。</green>"
  debug-on: "<prefix><yellow>デバッグモードを有効にしました。</yellow>"
  debug-off: "<prefix><yellow>デバッグモードを無効にしました。</yellow>"

cost:
  not-enough-level: "<prefix><red>レベルが足りません。必要: <yellow><cost> Lv</yellow></red>"
  not-enough-points: "<prefix><red>経験値が足りません。必要: <yellow><cost> pt</yellow></red>"
  consumed-level: "<prefix><gray>レベルを <yellow><cost></yellow> 消費しました。</gray>"
  consumed-points: "<prefix><gray>経験値を <yellow><cost> pt</yellow> 消費しました。</gray>"

cooldown:
  active: "<prefix><red>クールダウン中です。あと <yellow><cooldown></yellow> 秒</red>"

warmup:
  start: "<prefix><aqua>テレポートまで <yellow><warmup></yellow> 秒…動かないでください。</aqua>"
  cancelled-move: "<prefix><red>動いたためテレポートをキャンセルしました。</red>"
  cancelled-damage: "<prefix><red>ダメージを受けたためキャンセルしました。</red>"

combat:
  blocked: "<prefix><red>戦闘中はテレポートできません(あと <yellow><cooldown></yellow> 秒)。</red>"

safety:
  unsafe-cancelled: "<prefix><red>着地点が危険なためキャンセルしました。</red>"
  no-safe-location: "<prefix><red>近くに安全な場所が見つかりませんでした。</red>"

world:
  blacklisted: "<prefix><red>このワールドではこのテレポートは使用できません。</red>"
  worldguard-denied: "<prefix><red>このエリアではテレポートは許可されていません。</red>"

home:
  teleporting: "<prefix><green>home <yellow><home></yellow> にテレポートします。</green>"
  set: "<prefix><green>home <yellow><home></yellow> を設定しました。</green>"
  deleted: "<prefix><green>home <yellow><home></yellow> を削除しました。</green>"
  not-found: "<prefix><red>home <yellow><home></yellow> は存在しません。</red>"
  limit-reached: "<prefix><red>home の上限に達しました(<count>/<max>)。</red>"

warp:
  teleporting: "<prefix><green>warp <yellow><warp></yellow> にテレポートします。</green>"
  set: "<prefix><green>warp <yellow><warp></yellow> を設定しました。</green>"
  deleted: "<prefix><green>warp <yellow><warp></yellow> を削除しました。</green>"
  not-found: "<prefix><red>warp <yellow><warp></yellow> は存在しません。</red>"

tpa:
  sent: "<prefix><green><target></green> にTPリクエストを送信しました。"
  received: "<prefix><yellow><player></yellow> からTPリクエスト 〈<click:run_command:'/tpacceptx'><hover:show_text:'クリックで承認'><green>[承認]</green></hover></click> <click:run_command:'/tpdenyx'><hover:show_text:'クリックで拒否'><red>[拒否]</red></hover></click>〉"
  received-here: "<prefix><yellow><player></yellow> があなたを呼んでいます 〈<click:run_command:'/tpacceptx'><green>[承認]</green></click> <click:run_command:'/tpdenyx'><red>[拒否]</red></click>〉"
  accepted: "<prefix><green>リクエストを承認しました。</green>"
  denied: "<prefix><red>リクエストを拒否しました。</red>"
  expired: "<prefix><gray>TPリクエストが期限切れになりました。</gray>"
  no-pending: "<prefix><red>保留中のTPリクエストはありません。</red>"

rtp:
  searching: "<prefix><aqua>ランダム座標を探索中…</aqua>"
  teleporting: "<prefix><green><world> (<x>, <y>, <z>) にテレポートします。</green>"

tppos:
  teleporting: "<prefix><green>(<x>, <y>, <z>) <world> にテレポートします。</green>"

back:
  teleporting: "<prefix><green>直前の位置に戻ります。</green>"
  no-previous: "<prefix><red>戻れる位置がありません。</red>"
```

`lang/en_US.yml` も同じキー構造で英語版を同梱します。

## 7. ディレクトリ構成

```
simple-xp-teleport/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── README.md
├── LICENSE
└── src/
    └── main/
        ├── java/
        │   └── com/example/sxt/
        │       ├── SimpleXpTeleportPlugin.java
        │       ├── command/
        │       │   ├── HomeCommand.java
        │       │   ├── SetHomeCommand.java
        │       │   ├── DelHomeCommand.java
        │       │   ├── WarpCommand.java
        │       │   ├── SetWarpCommand.java
        │       │   ├── DelWarpCommand.java
        │       │   ├── TpaCommand.java
        │       │   ├── TpaHereCommand.java
        │       │   ├── TpaAcceptCommand.java
        │       │   ├── TpaDenyCommand.java
        │       │   ├── RtpCommand.java
        │       │   ├── TpPosCommand.java
        │       │   ├── BackCommand.java
        │       │   └── admin/SxtAdminCommand.java
        │       ├── teleport/
        │       │   ├── TeleportService.java
        │       │   ├── TeleportRequest.java
        │       │   ├── WarmupTask.java
        │       │   ├── CooldownManager.java
        │       │   ├── CombatTagManager.java
        │       │   ├── SafetyChecker.java
        │       │   └── RandomLocationFinder.java
        │       ├── cost/
        │       │   ├── CostCalculator.java
        │       │   ├── CostMode.java          (enum: LEVEL, POINTS)
        │       │   ├── CostType.java          (enum: FIXED, DISTANCE)
        │       │   └── XpUtil.java            (総経験値計算)
        │       ├── data/
        │       │   ├── DatabaseManager.java
        │       │   ├── dao/HomeDao.java
        │       │   ├── dao/WarpDao.java
        │       │   ├── dao/BackLocationDao.java
        │       │   └── model/{Home,Warp,BackLocation}.java
        │       ├── config/
        │       │   ├── PluginConfig.java
        │       │   └── CommandConfig.java
        │       ├── message/
        │       │   ├── MessageService.java    (MiniMessage パーサー)
        │       │   └── LangLoader.java
        │       ├── permission/
        │       │   └── HomeLimitResolver.java (sxt.homes.max.<n>)
        │       ├── listener/
        │       │   ├── PlayerMoveListener.java   (詠唱キャンセル)
        │       │   ├── EntityDamageListener.java (詠唱キャンセル / 戦闘タグ)
        │       │   ├── PlayerDeathListener.java  (back用)
        │       │   └── PlayerTeleportListener.java
        │       ├── hook/
        │       │   ├── PlaceholderApiHook.java
        │       │   └── WorldGuardHook.java
        │       └── util/
        │           ├── AuditLogger.java
        │           └── DebugLogger.java
        └── resources/
            ├── plugin.yml
            ├── config.yml
            └── lang/
                ├── ja_JP.yml
                └── en_US.yml
```

## 8. 依存ライブラリ

| 種別 | 依存 | 用途 |
|-----|------|------|
| compileOnly | `io.papermc.paper:paper-api:1.21.x-R0.1-SNAPSHOT` | Paper API(paperweight-userdev 経由) |
| implementation | `org.xerial:sqlite-jdbc:3.46.x` | SQLite ドライバ |
| compileOnly (soft) | `me.clip:placeholderapi:2.11.x` | PlaceholderAPI 連携 |
| compileOnly (soft) | `com.sk89q.worldguard:worldguard-bukkit:7.0.x` | WorldGuard 連携 |

Adventure / MiniMessage は Paper API に内包されているため追加依存不要。

## 9. ビルド方法

### 9.1 必要環境
- JDK 21(Temurin 推奨)
- Gradle Wrapper(リポジトリ同梱)

### 9.2 ビルド手順

```bash
# クローン後、プロジェクトルートで:
./gradlew build

# 成果物:
# build/libs/simple-xp-teleport-<version>.jar
```

### 9.3 開発用テストサーバー起動

```bash
# paperweight-userdev の runServer タスクで即座にテストサーバー起動
./gradlew runServer
```

### 9.4 build.gradle.kts 概要

```kotlin
plugins {
    java
    id("io.papermc.paperweight.userdev") version "1.7.x"
    id("xyz.jpenilla.run-paper") version "2.3.x"
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
    paperweight.paperDevBundle("1.21.x-R0.1-SNAPSHOT")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.11")
}

tasks {
    runServer { minecraftVersion("1.21.4") }
    assemble { dependsOn(reobfJar) }
}
```

### 9.5 plugin.yml(抜粋)

```yaml
name: SimpleXpTeleport
version: '${project.version}'
main: com.example.sxt.SimpleXpTeleportPlugin
api-version: '1.21'
authors: [YourName]
softdepend: [PlaceholderAPI, WorldGuard]
commands:
  homex: { description: "homeへテレポート", usage: "/homex [name]" }
  sethomex: { usage: "/sethomex [name]" }
  delhomex: { usage: "/delhomex [name]" }
  warpx: { usage: "/warpx <name>" }
  setwarpx: { usage: "/setwarpx <name>" }
  delwarpx: { usage: "/delwarpx <name>" }
  tpax: { usage: "/tpax <player>" }
  tpahere: { usage: "/tpahere <player>" }
  tpacceptx: { usage: "/tpacceptx" }
  tpdenyx: { usage: "/tpdenyx" }
  rtpx: { usage: "/rtpx [world]" }
  tpposx: { usage: "/tpposx <x> <y> <z> [world]" }
  backx: { usage: "/backx" }
  sxtadmin: { usage: "/sxtadmin <reload|debug|...>" }
permissions:
  # (権限一覧の表に準ずる)
```
