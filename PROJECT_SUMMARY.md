# Encoding Batch プロジェクト サマリー

## 📋 プロジェクト概要

文字コード変換バッチアプリケーション。UTF-8、JIS、EBCDICの相互変換をサポートし、6種類のファイルタイプに対応。

## 🎯 主要機能

### サポートする文字コード
- **UTF-8**: Unicode (可変長)
- **JIS**: JIS X 0201 (1バイト) + ISO-2022-JP (2バイト、ESCシーケンス自動処理)
- **EBCDIC**: CP930 (IBM EBCDIC、SBCS/DBCS混在)

### サポートするファイルタイプ

| ID | レコード長 | 文字タイプ | 変換方式 |
|----|-----------|-----------|---------|
| FILE_A | 210B固定 | 1Bのみ | 全体変換 |
| FILE_B | 210B固定 | 1Bのみ | 全体変換 |
| FILE_C | 380B固定 | 1B+2B混合 | フィールド単位変換 |
| FILE_D | 380B固定 | 1B+2B混合 | フィールド単位変換(データ種別対応) |
| FILE_E | 可変長 | 制御文字あり | 全体変換+制御文字置換 |
| FILE_F | 可変長 | 制御文字あり | 全体変換+制御文字置換 |

## 📁 成果物一覧

### 1. ドキュメント
- `README.md` - セットアップ・実行ガイド
- `docs/class-diagram.puml` - クラス図(PlantUML)
- `docs/sequence-diagram.puml` - シーケンス図(PlantUML)

### 2. ソースコード

#### メイン
- `EncodingBatchMain.java` - エントリーポイント

#### Config (設定・定義)
- `BatchParameters.java` - パラメータ管理、バリデーション
- `FileType.java` - ファイルタイプEnum(6種類)

#### Processor (レコード処理)
- `RecordProcessor.java` - 基底抽象クラス
- `FileABProcessor.java` - FILE_A/B用(全体変換)
- `FileCDProcessor.java` - FILE_C/D用(部分変換)
- `FileEFProcessor.java` - FILE_E/F用(可変長)

#### Model (データモデル)
- `FieldDefinition.java` - フィールド定義(バイト位置/文字位置)

#### Util (ユーティリティ)
- `CodeConverter.java` - 文字コード変換(公開メソッド2つ)
  - `convertCharset()` - 1バイト文字変換
  - `convertDoubleByteCharset()` - 2バイト文字変換

### 3. 設定ファイル
- `pom.xml` - Maven設定
- `logback.xml` - ログ設定
- `config/sample-*.properties` - サンプルパラメータファイル(4種類)

## 🔧 技術仕様

### CodeConverter クラスの主要機能

#### 公開メソッド
```java
// 通常の文字コード変換(1バイト文字)
public static byte[] convertCharset(
    byte[] source, 
    Charset sourceCharset, 
    Charset targetCharset
) throws CharacterCodingException

// 2バイト文字(漢字)の変換
public static byte[] convertDoubleByteCharset(
    byte[] source, 
    Charset sourceCharset, 
    Charset targetCharset
) throws CharacterCodingException
```

#### 内部処理(privateメソッド)
- `addEscapeSequence()` - ISO-2022-JP用ESCシーケンス付与
- `removeEscapeSequence()` - ISO-2022-JP用ESCシーケンス除去
- `convertControlCharacter()` - 制御文字変換(0xB4 ↔ 0x74)

### ISO-2022-JPの自動処理

**変換元がISO-2022-JPの場合**:
```
入力: [漢字データ] (ESCシーケンスなし)
 ↓ 内部処理
処理: ESC $ B [漢字データ] ESC ( B
 ↓ 変換
出力: [変換後データ]
```

**変換先がISO-2022-JPの場合**:
```
入力: [データ]
 ↓ 変換
処理: ESC $ B [漢字データ] ESC ( B
 ↓ 内部処理
出力: [漢字データ] (ESCシーケンス除去)
```

### UTF-8入力時の特殊処理

固定長ファイル(FILE_C, FILE_D)でUTF-8入力の場合:

1. **文字数ベース**でフィールドを抽出
   - UTF-8は可変長エンコーディングのため
   - レコード定義を**文字位置**で管理

2. **前提条件**
   - UTF-8ファイルには漢字が含まれない
   - 英数字・半角カナのみ

3. **処理フロー**
   ```
   UTF-8入力: 380文字
   ↓ 文字位置でフィールド抽出
   ↓ JIS/EBCDIC変換
   出力: 380バイト固定長
   ```

### 可変長レコード(FILE_E, FILE_F)

レコード構造:
```
[0-3B] ブロック長 (ビッグエンディアン)
[4-7B] レコード長 (ビッグエンディアン)
[8B~]  データ部分
```

制御文字変換:
- EBCDIC → JIS/UTF-8: `0xB4` → `0x74`
- JIS/UTF-8 → EBCDIC: `0x74` → `0xB4`

## 🚀 クイックスタート

### ビルド
```bash
mvn clean package
```

### 実行
```bash
java -jar target/encoding-batch-1.0.0-jar-with-dependencies.jar \
  config/sample-file-c-utf8-to-jis.properties
```

### パラメータファイル例
```properties
input.file.path=/data/input.dat
output.file.path=/data/output.dat
source.charset.single=UTF-8
source.charset.double=UTF-8
target.charset.single=JIS_X_0201
target.charset.double=ISO-2022-JP
file.id=FILE_C
```

## 📊 処理フロー

### FILE_A/B (シンプル全体変換)
```
入力レコード(210B)
  ↓
CodeConverter.convertCharset()
  ↓
出力レコード
```

### FILE_C/D (混合文字コード部分変換)
```
入力レコード(380B or 380文字)
  ↓
レコード種別判定(ヘッダー/データ)
  ↓
ヘッダー: 全体を1B変換
データ: フィールド単位で1B/2B変換
  ├─ 1Bフィールド: convertCharset()
  └─ 2Bフィールド: convertDoubleByteCharset()
  ↓
出力レコード(380B)
```

### FILE_E/F (可変長レコード)
```
入力レコード
  ↓
ブロック長・レコード長読み込み
  ↓
データ部分抽出(8B以降)
  ↓
CodeConverter.convertCharset()
(制御文字変換含む)
  ↓
新しい長さ計算・更新
  ↓
出力レコード
```

## 🏗️ アーキテクチャの特徴

### 1. 責務の分離
- **CodeConverter**: 純粋な文字コード変換のみ(レイアウト情報を持たない)
- **RecordProcessor**: レコード構造の解析とフィールド抽出
- **BatchParameters**: パラメータ管理とバリデーション

### 2. 拡張性
- 新しいファイルタイプは`RecordProcessor`を継承して追加
- `FileType` Enumに定義を追加するだけ

### 3. 再利用性
- `CodeConverter`は他プロジェクトでも使用可能な汎用Util

## ⚠️ エラー処理

### 終了コード
- `0`: 正常終了
- `1`: コマンドライン引数エラー
- `2`: パラメータ検証エラー
- `3`: 文字コード変換エラー
- `99`: その他のエラー

### エラー時の動作
- 変換エラー発生時、**即座に終了**
- エラー対象(レコード番号等)をログ出力
- 部分的に生成された出力ファイルは削除されない

## 📝 レコード定義詳細

### FILE_C データレコード

**バイトベース(JIS/EBCDIC)**:
- 漢字フィールド1: 50-99バイト (25文字 = 50バイト)
- 漢字フィールド2: 150-199バイト
- 漢字フィールド3: 300-349バイト

**文字ベース(UTF-8)**:
- 漢字フィールド1: 50-74文字 (25文字)
- 漢字フィールド2: 150-174文字
- 漢字フィールド3: 300-324文字

### FILE_D データレコード

**データ種別='1'の場合(バイトベース)**:
- 漢字フィールド1: 100-149バイト
- 漢字フィールド2: 200-249バイト

**データ種別='2'の場合(バイトベース)**:
- 漢字フィールド1: 120-169バイト
- 漢字フィールド2: 250-299バイト

(UTF-8の場合も同様に文字位置で定義)

## 🔍 クラス階層

```
EncodingBatchMain (メイン)
    ↓ 使用
BatchParameters (パラメータ管理)
FileType (Enum)
    ↓ 選択
RecordProcessor (抽象基底)
    ├─ FileABProcessor (FILE_A/B)
    ├─ FileCDProcessor (FILE_C/D)
    └─ FileEFProcessor (FILE_E/F)
        ↓ 使用
    CodeConverter (Util)
    FieldDefinition (モデル)
```

## 📦 依存ライブラリ

- SLF4J 2.0.9 (ログAPI)
- Logback 1.4.11 (ログ実装)
- JUnit 5.10.0 (テスト、scopeはtest)

## 📚 追加ドキュメント

プロジェクト内の以下のファイルを参照してください:

1. **README.md** - 詳細なセットアップと実行ガイド
2. **docs/class-diagram.puml** - PlantUML形式のクラス図
3. **docs/sequence-diagram.puml** - PlantUML形式のシーケンス図

PlantUML図の表示方法:
- オンライン: https://www.plantuml.com/plantuml/uml/
- VSCode: PlantUML拡張機能
- IntelliJ IDEA: PlantUMLプラグイン

## ✅ チェックリスト

実装完了項目:
- [x] Maven pom.xml
- [x] 6種類のファイルタイプ対応
- [x] UTF-8/JIS/EBCDIC相互変換
- [x] ISO-2022-JP ESCシーケンス自動処理
- [x] UTF-8入力時の文字数ベース処理
- [x] 可変長レコード対応
- [x] 制御文字変換(0xB4 ↔ 0x74)
- [x] 包括的なバリデーション
- [x] エラー処理とログ出力
- [x] クラス図とシーケンス図
- [x] 詳細なREADME
- [x] サンプルパラメータファイル(4種類)

---

**作成日**: 2026-02-06  
**バージョン**: 1.0.0  
**Java バージョン**: 11以上  
**ビルドツール**: Maven 3.6以上
