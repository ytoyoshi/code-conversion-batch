# Encoding Batch Application

文字コード変換を行うJavaバッチアプリケーション。UTF-8、JIS、EBCDICの相互変換をサポートし、複数のファイルタイプに対応。

## 概要

このバッチアプリケーションは、メインフレームシステム統合シナリオにおける文字コード変換を目的として開発されました。

### サポートする文字コード

- **UTF-8**: Unicode (可変長、1-4バイト)
- **JIS**: 
  - 1バイト文字: JIS X 0201 (半角カナ・英数字)
  - 2バイト文字: ISO-2022-JP (7bit JIS、漢字)
- **EBCDIC**: CP930 (IBM EBCDIC、SBCS/DBCSホスト混在)

### サポートするファイルタイプ

| ファイルID | レコード長 | 特徴 | 変換タイプ |
|-----------|-----------|------|-----------|
| FILE_A | 210バイト固定 | 1バイト文字のみ | 全体変換 |
| FILE_B | 210バイト固定 | 1バイト文字のみ | 全体変換 |
| FILE_C | 380バイト固定 | 1バイト+2バイト混合 | 部分変換(フィールド単位) |
| FILE_D | 380バイト固定 | 1バイト+2バイト混合 | 部分変換(フィールド単位、データ種別により異なる) |
| FILE_E | 可変長 | 制御文字変換あり | 全体変換+制御文字置換 |
| FILE_F | 可変長 | 制御文字変換あり | 全体変換+制御文字置換 |

## プロジェクト構成

```
encoding-batch/
├── pom.xml                                 # Maven設定
├── README.md                               # このファイル
├── docs/
│   ├── class-diagram.puml                  # クラス図(PlantUML)
│   └── sequence-diagram.puml               # シーケンス図(PlantUML)
├── config/
│   ├── sample-file-a-utf8-to-jis.properties
│   ├── sample-file-c-utf8-to-jis.properties
│   ├── sample-file-d-jis-to-ebcdic.properties
│   └── sample-file-e-ebcdic-to-utf8.properties
└── src/main/java/com/example/batch/
    ├── EncodingBatchMain.java              # メインクラス
    ├── config/
    │   ├── BatchParameters.java            # パラメータ管理
    │   └── FileType.java                   # ファイルタイプEnum
    ├── processor/
    │   ├── RecordProcessor.java            # レコード処理基底クラス
    │   ├── FileABProcessor.java            # FILE_A, B用プロセッサ
    │   ├── FileCDProcessor.java            # FILE_C, D用プロセッサ
    │   └── FileEFProcessor.java            # FILE_E, F用プロセッサ
    ├── model/
    │   └── FieldDefinition.java            # フィールド定義
    └── util/
        └── CodeConverter.java              # 文字コード変換Util
```

## ビルド方法

### 前提条件

- Java 11以上
- Maven 3.6以上

### ビルドコマンド

```bash
# プロジェクトのビルド
mvn clean package

# 依存関係を含むFat JARの生成
mvn clean package assembly:single
```

ビルド成果物:
- `target/encoding-batch-1.0.0.jar` - 通常のJAR
- `target/encoding-batch-1.0.0-jar-with-dependencies.jar` - 依存関係を含むFat JAR

## 実行方法

### 基本的な実行コマンド

```bash
java -jar target/encoding-batch-1.0.0-jar-with-dependencies.jar <parameter-file-path>
```

### パラメータファイルの形式

パラメータファイルは `key=value` 形式のプロパティファイルです。

```properties
# 入出力ファイルパス
input.file.path=/path/to/input.dat
output.file.path=/path/to/output.dat

# 変換元文字コード
source.charset.single=UTF-8
source.charset.double=UTF-8

# 変換先文字コード
target.charset.single=JIS_X_0201
target.charset.double=ISO-2022-JP

# ファイルID (FILE_A, FILE_B, FILE_C, FILE_D, FILE_E, FILE_F)
file.id=FILE_C
```

### パラメータの詳細

| パラメータ | 必須 | 説明 | 例 |
|-----------|------|------|-----|
| input.file.path | ✓ | 入力ファイルパス | /data/input.dat |
| output.file.path | ✓ | 出力ファイルパス | /data/output.dat |
| source.charset.single | ✓ | 変換元1バイト文字コード | UTF-8, JIS_X_0201, CP930 |
| source.charset.double | ✓ | 変換元2バイト文字コード | UTF-8, ISO-2022-JP, CP930 |
| target.charset.single | ✓ | 変換先1バイト文字コード | UTF-8, JIS_X_0201, CP930 |
| target.charset.double | ✓ | 変換先2バイト文字コード | UTF-8, ISO-2022-JP, CP930 |
| file.id | ✓ | ファイルタイプID | FILE_A ~ FILE_F |

### 文字コード指定のパターン

#### UTF-8の場合
1バイト/2バイトの区別がないため、両方に同じ値を指定:
```properties
source.charset.single=UTF-8
source.charset.double=UTF-8
```

#### JIS混合の場合
1バイトと2バイトで異なる文字コードを指定:
```properties
source.charset.single=JIS_X_0201
source.charset.double=ISO-2022-JP
```

#### EBCDIC(CP930)の場合
CP930はSBCS/DBCS混在をサポートするため、両方に同じ値を指定:
```properties
source.charset.single=CP930
source.charset.double=CP930
```

## 実行例

### 例1: FILE_A (UTF-8 → JIS)

```bash
java -jar target/encoding-batch-1.0.0-jar-with-dependencies.jar \
  config/sample-file-a-utf8-to-jis.properties
```

### 例2: FILE_C (UTF-8 → JIS混合)

```bash
java -jar target/encoding-batch-1.0.0-jar-with-dependencies.jar \
  config/sample-file-c-utf8-to-jis.properties
```

### 例3: FILE_D (JIS混合 → EBCDIC)

```bash
java -jar target/encoding-batch-1.0.0-jar-with-dependencies.jar \
  config/sample-file-d-jis-to-ebcdic.properties
```

### 例4: FILE_E (EBCDIC → UTF-8、可変長)

```bash
java -jar target/encoding-batch-1.0.0-jar-with-dependencies.jar \
  config/sample-file-e-ebcdic-to-utf8.properties
```

## 処理仕様

### FILE_A, FILE_B: シンプル全体変換

- 210バイト固定長
- 1バイト文字のみ
- レコード全体を一括変換

### FILE_C, FILE_D: 混合文字コード部分変換

#### FILE_C
- 380バイト(またはUTF-8の場合380文字)固定長
- ヘッダーレコード(先頭バイト='1'): 1バイト文字として全体変換
- データレコード(先頭バイト='2'): フィールド単位で変換
  - 漢字フィールド位置(バイトベース):
    - 50-99バイト目 (25文字)
    - 150-199バイト目 (25文字)
    - 300-349バイト目 (25文字)
  - 漢字フィールド位置(UTF-8、文字ベース):
    - 50-74文字目 (25文字)
    - 150-174文字目 (25文字)
    - 300-324文字目 (25文字)

#### FILE_D
- 380バイト(またはUTF-8の場合380文字)固定長
- ヘッダーレコード(先頭バイト='1'): 1バイト文字として全体変換
- データレコード(先頭バイト='2'): 2バイト目のデータ種別により処理が変わる
  - データ種別='1'の場合(バイトベース):
    - 100-149バイト目 (25文字)
    - 200-249バイト目 (25文字)
  - データ種別='2'の場合(バイトベース):
    - 120-169バイト目 (25文字)
    - 250-299バイト目 (25文字)
  - データ種別='1'の場合(UTF-8、文字ベース):
    - 100-124文字目 (25文字)
    - 200-224文字目 (25文字)
  - データ種別='2'の場合(UTF-8、文字ベース):
    - 120-144文字目 (25文字)
    - 250-274文字目 (25文字)

### FILE_E, FILE_F: 可変長レコード

レコード構造:
```
[0-3バイト] ブロック長 (4バイト、ビッグエンディアン)
[4-7バイト] レコード長 (4バイト、ビッグエンディアン)
[8バイト以降] データ部分(変換対象)
```

制御文字変換:
- EBCDIC(CP930): 0xB4 → JIS/UTF-8: 0x74
- JIS/UTF-8: 0x74 → EBCDIC(CP930): 0xB4

変換後、データ長の変化に応じてブロック長・レコード長を更新。

### UTF-8入力時の特殊処理

UTF-8は可変長エンコーディングのため、固定長ファイル(FILE_C, FILE_D)の処理時は**文字数ベース**でフィールドを抽出します。

- UTF-8入力ファイル: 380**文字**で構成
- 各フィールドを文字位置で抽出
- JIS/EBCDICに変換後、固定長380**バイト**になる

注意: UTF-8ファイルには**漢字が含まれない**(英数字・半角カナのみ)という前提があります。

## エラー処理

### 終了コード

| コード | 説明 |
|-------|------|
| 0 | 正常終了 |
| 1 | コマンドライン引数エラー |
| 2 | パラメータ検証エラー |
| 3 | 文字コード変換エラー |
| 99 | その他の予期しないエラー |

### エラー時の動作

- 文字コード変換エラー発生時、即座に処理を終了
- エラー対象(レコード番号等)をログに出力
- 処理済みの出力ファイルは削除されない(部分的に生成される)

## ログ出力

ログは以下の場所に出力されます:

- コンソール: 標準出力
- ファイル: `logs/encoding-batch.log`
- ローテーション: 日次(最大30日保持)

ログレベル:
- INFO: バッチの開始・終了、処理サマリー
- DEBUG: レコード処理の詳細(1000レコードごと)
- ERROR: エラー情報

## 技術仕様

### ISO-2022-JPのESCシーケンス処理

2バイト文字(漢字)の変換時、ISO-2022-JPのESCシーケンスを自動的に処理:

- **変換元がISO-2022-JPの場合**: 
  - 入力データにはESCシーケンスが含まれない想定
  - 内部でESCシーケンスを付与して変換
  - `ESC $ B [漢字データ] ESC ( B`

- **変換先がISO-2022-JPの場合**: 
  - 変換後にESCシーケンスを除去
  - 純粋な漢字データのみを返却

### 制御文字変換

FILE_E, FILE_Fの可変長レコード処理時に制御文字を変換:

| 変換元 | 変換先 | 16進値 |
|-------|-------|--------|
| EBCDIC 'u' | JIS/UTF-8 't' | 0xB4 → 0x74 |
| JIS/UTF-8 't' | EBCDIC 'u' | 0x74 → 0xB4 |

## アーキテクチャ

### クラス設計

主要なクラスとその責務:

- **EncodingBatchMain**: エントリーポイント、全体制御
- **BatchParameters**: パラメータ管理、バリデーション
- **FileType**: ファイルタイプ定義(Enum)
- **CodeConverter**: 文字コード変換ロジック(Utility)
- **RecordProcessor**: レコード処理の基底クラス
  - **FileABProcessor**: FILE_A/B用(全体変換)
  - **FileCDProcessor**: FILE_C/D用(部分変換)
  - **FileEFProcessor**: FILE_E/F用(可変長)
- **FieldDefinition**: フィールド定義(バイト位置/文字位置)

詳細は `docs/class-diagram.puml` および `docs/sequence-diagram.puml` を参照してください。

### 設計原則

1. **単一責任の原則**: CodeConverterは純粋に文字コード変換のみ、レイアウト情報を持たない
2. **拡張性**: 新しいファイルタイプは新しいProcessorクラスを追加するだけで対応可能
3. **再利用性**: CodeConverterは他プロジェクトでも利用可能な汎用Util
4. **テスタビリティ**: 各コンポーネントは独立してテスト可能

## トラブルシューティング

### 文字コード変換エラー

**症状**: `CharacterCodingException` が発生

**原因と対策**:
1. マッピングできない文字が含まれている
   - 入力ファイルの文字コードを確認
   - サポートされていない文字が含まれていないか確認

2. ESCシーケンスの問題(ISO-2022-JP)
   - ISO-2022-JP入力時、ESCシーケンスが既に含まれている場合は事前に除去

3. ファイル破損
   - 入力ファイルが破損していないか確認

### レコード長エラー

**症状**: `Incomplete record read` エラー

**原因と対策**:
- 固定長ファイルのレコード長が不正
- ファイル末尾が切れている可能性
- ファイルタイプIDが正しいか確認

### パラメータエラー

**症状**: `Parameter validation error`

**原因と対策**:
- 必須パラメータが不足していないか確認
- 文字コード名が正しいか確認(UTF-8, JIS_X_0201, ISO-2022-JP, CP930)
- ファイルIDが正しいか確認(FILE_A ~ FILE_F)

## ライセンス

(ライセンス情報を記載)

## 作成者

(作成者情報を記載)
