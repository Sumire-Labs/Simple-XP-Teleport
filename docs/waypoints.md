# Waypoints (ウェイポイント) 機能ドキュメント

## 概要

`/wayx` コマンドを使用して、プレイヤー個人のウェイポイント（地点登録）を作成・管理・共有できます。
ウェイポイントはプレイヤーごとに SQLite データベースに永続化され、GUI またはコマンドで操作できます。

## コマンド一覧

| コマンド | 引数 | 説明 | 権限 |
|---------|------|------|------|
| `/wayx` | - | ウェイポイント一覧 GUI を開く | `sxt.use.wayx` |
| `/wayx add <name>` | ウェイポイント名（半角英数字・アンダースコア・ハイフン 1〜32 文字） | 現在位置をウェイポイントとして追加 | `sxt.use.wayx` |
| `/wayx list` | - | ウェイポイント管理 GUI を開く（削除・共有操作が可能） | `sxt.use.wayx` |
| `/wayx remove <name>` | ウェイポイント名 | 指定したウェイポイントを削除 | `sxt.use.wayx` |
| `/wayx share <name> <player>` | ウェイポイント名, 共有先プレイヤー | 他プレイヤーにウェイポイントを共有リクエスト送信 | `sxt.use.wayx.share` |
| `/wayx accept` | - | 保留中のウェイポイント共有リクエストを承認 | `sxt.use.wayx` |
| `/wayx deny` | - | 保留中のウェイポイント共有リクエストを拒否 | `sxt.use.wayx` |

## 操作シナリオ

### 1. ウェイポイントの追加 (`/wayx add`)

```
/wayx add mine_entrance
# 出力: [SXT] waypoint mine_entrance を追加しました。
```

- プレイヤーの現在位置（ワールド・座標・向き）がウェイポイントとして保存されます。
- 同じ名前のウェイポイントが既に存在する場合は上書きされます（上書きは上限チェックをスキップします）。
- 新規追加時は上限チェックが行われ、上限を超える場合はエラーが表示されます。

### 2. ウェイポイント一覧の表示 (`/wayx`)

```
/wayx
# 54 スロットの GUI が開き、自身のウェイポイント一覧が表示されます。
```

GUI 構成:
- 上部 45 スロット（0〜44）: ウェイポイントアイテム（エンダーパール）
- スロット 45: 前のページ（矢印）
- スロット 47: 「+ Add New Waypoint」（エメラルドブロック）
- スロット 49: 「☰ Manage Waypoints」（チェスト）
- スロット 53: 次のページ（矢印）

操作:
- ウェイポイントをクリック → テレポートを開始
- 「追加」ボタンをクリック → チャットでウェイポイント名を入力して作成
- 「管理」ボタンをクリック → 管理 GUI に移動

### 3. ウェイポイント管理 (`/wayx list`)

```
/wayx list
# 管理 GUI が開きます。
```

管理 GUI 構成:
- 上部 45 スロット: ウェイポイントアイテム
- スロット 45: 前のページ / スロット 47: 追加 / スロット 53: 次のページ

操作:
- ウェイポイントをクリック → 操作選択 GUI が開く（テレポート / 削除 / 共有 / 戻る）

### 4. ウェイポイントの削除 (`/wayx remove` または GUI)

```
/wayx remove mine_entrance
# 出力: [SXT] waypoint mine_entrance を削除しました。
```

管理 GUI でも:
1. 削除したいウェイポイントをクリック
2. 「Delete」（バリアブロック）をクリック

### 5. ウェイポイントの共有 (`/wayx share`)

```
/wayx share mine_entrance Steve
# 出力: [SXT] Steve に waypoint mine_entrance の共有リクエストを送信しました。
```

共有の流れ:
1. 送信者が `/wayx share <name> <player>` を実行
2. 受信者にクリック可能なメッセージが表示される: `[Accept] [Deny]`
3. 受信者が `/wayx accept` または [Accept] をクリック → ウェイポイントが受信者のコレクションにコピーされる
4. 受信者が `/wayx deny` または [Deny] をクリック → リクエストが拒否される

GUI からの共有:
1. 管理 GUI でウェイポイントをクリック → 操作選択 GUI
2. 「Share」（本と羽根ペン）をクリック
3. オンラインプレイヤー一覧から共有先を選択
4. 選択したプレイヤーに共有リクエストが送信される

### 6. 共有リクエストの承認 (`/wayx accept`)

```
/wayx accept
# 出力: [SXT] Steve から waypoint mine_entrance を受け取りました。
```

- 受信者側のウェイポイント上限がチェックされます。
- `waypoints.share.charge-on-accept: true` の場合、承認時に受信者に XP コストが課金されます。
- コスト不足の場合は保留状態が維持され、再試行できます。
- リクエストは設定された有効期限（デフォルト 60 秒）を過ぎると自動失効します。

### 7. 共有リクエストの拒否 (`/wayx deny`)

```
/wayx deny
# 出力: [SXT] waypoint mine_entrance の共有リクエストが拒否されました。
# 送信者にも拒否通知が表示されます。
```

### 8. GUI からのウェイポイント追加

1. 一覧 GUI または管理 GUI で「+ Add New Waypoint」をクリック
2. チャットメッセージ「チャットで waypoint 名を入力してください (cancel でキャンセル)。」が表示される
3. チャットでウェイポイント名を入力（60 秒以内）
4. `cancel` と入力するとキャンセル
5. 作成成功後、元の GUI に自動で戻る

---

## 権限一覧

| 権限ノード | 説明 | デフォルト |
|-----------|------|-----------|
| `sxt.use.wayx` | `/wayx` の使用、ウェイポイントの追加・表示・削除・承認・拒否 | `true` |
| `sxt.use.wayx.share` | `/wayx share` による他プレイヤーへの共有 | `true` |
| `sxt.waypoints.max.<n>` | ウェイポイント所持上限を `<n>` に拡張（例: `sxt.waypoints.max.20`） | `false` |
| `sxt.waypoints.unlimited` | ウェイポイント所持上限を無制限に設定 | `op` |
| `sxt.bypass.cost.wayx` | ウェイポイントテレポート時の XP 消費をバイパス | `false` |
| `sxt.bypass.cost.*` | 全コマンドの XP 消費をバイパス | `false` |

権限ツリー:
- `sxt.use.*` は `sxt.use.wayx` と `sxt.use.wayx.share` を包含します。
- `sxt.*` は `sxt.use.*` と `sxt.waypoints.unlimited` を包含します（XP コストバイパスは含まれません）。

---

## コンフィグ設定 (`config.yml`)

### ウェイポイント基本設定

```yaml
waypoints:
  max-per-player: 10          # 1プレイヤーあたりの最大ウェイポイント数（デフォルト）
  share:
    enabled: true             # 共有機能の有効/無効
    expire-seconds: 60        # 共有リクエストの有効期限（秒）
    charge-on-accept: false   # 共有承認時に受諾者にも XP コストを課すか
```

### ウェイポイントテレポートコスト設定

```yaml
commands:
  wayx:
    cost:
      mode: LEVEL       # コストモード: LEVEL（レベル）または POINTS（経験値ポイント）
      type: FIXED       # コスト計算方式: FIXED（固定）または DISTANCE（距離連動）
      amount: 2         # 基本コスト
      cross-world-extra: 3  # クロスワールド時の追加コスト
    cooldown: 3         # クールダウン（秒）
    warmup: 2           # テレポート詠唱時間（秒）
    cancel-on-move: true     # 移動でキャンセル
    cancel-on-damage: true   # ダメージでキャンセル
    allow-in-combat: false   # 戦闘中テレポート許可
    safety-check: NONE       # 安全チェックモード: NONE / CANCEL / FIND_SAFE
    blacklist-worlds: []     # 使用禁止ワールド
```

---

## データ永続化

### `waypoints` テーブル (SQLite)

| カラム名 | 型 | 説明 |
|---------|------|------|
| `id` | `INTEGER PRIMARY KEY AUTOINCREMENT` | 内部 ID |
| `owner_uuid` | `TEXT NOT NULL` | 所有者の UUID |
| `name` | `TEXT NOT NULL` | ウェイポイント名 |
| `world` | `TEXT NOT NULL` | ワールド名 |
| `x` | `REAL NOT NULL` | X 座標 |
| `y` | `REAL NOT NULL` | Y 座標 |
| `z` | `REAL NOT NULL` | Z 座標 |
| `yaw` | `REAL NOT NULL` | 水平回転 |
| `pitch` | `REAL NOT NULL` | 垂直回転 |
| `created_at` | `INTEGER NOT NULL` | 作成日時（エポックミリ秒） |
| `updated_at` | `INTEGER NOT NULL` | 更新日時（エポックミリ秒） |

- ユニーク制約: `UNIQUE(owner_uuid, name)` — 同じプレイヤーが同じ名前のウェイポイントを複数持つことはできない。
- インデックス: `idx_waypoints_owner` (on `owner_uuid`)
- データベースファイル: `plugins/Simple-XP-Teleport/data.db`

### 共有リクエスト

共有リクエストはメモリ上で管理され、サーバー再起動時にリセットされます。
有効期限超過分は 20 チック（1 秒）ごとの定期タスクで自動的にクリーンアップされます。

### 上限解決ロジック

ウェイポイント所持上限は以下の優先順位で解決されます:

1. `sxt.waypoints.unlimited` が付与されている → `Integer.MAX_VALUE`（無制限）
2. 付与されている `sxt.waypoints.max.<n>` の中で最大の `n`
3. 上記いずれもない場合 → `config.yml` の `waypoints.max-per-player`

### 共有承認時の XP コスト

`waypoints.share.charge-on-accept: true` 設定時、共有承認者は `commands.wayx.cost.*` に基づいて XP コストが課金されます。
コスト計算は送信者の現在位置とウェイポイントの距離に基づきます。
`sxt.bypass.cost.wayx` または `sxt.bypass.cost.*` が付与されているとコストは免除されます。
