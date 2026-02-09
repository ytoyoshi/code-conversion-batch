# 文字コード変換バッチアプリケーション - 納品物

## 納品日
2026年2月9日

## 納品物一覧

本アーカイブには以下のファイルが含まれています。

### 1. ソースコード

#### メインクラス
- `src/main/java/com/example/batch/EncodingBatchMain.java`
  - メイン処理クラス
  - パラメータ読み込み、ファイルタイプ判定、適切な処理の呼び出し

#### パラメータ管理
- `src/main/java/com/example/batch/param/BatchParameter.java`
  - 処理指示パラメータの読み込みとバリデーション

#### 定数定義
- `src/main/java/com/example/batch/constant/FileType.java`
  - ファイルタイプEnum（FILE_A～FILE_F）

#### 処理クラス
- `src/main/java/com/example/batch/processor/WholeFileConverter.java`
  - 全体変換処理（FILE_A、FILE_B用）
  - 1バイト文字のみのファイルを全体一括変換

- `src/main/java/com/example/batch/processor/MixedFileConverter.java`
  - 混合ファイル変換処理（FILE_C、FILE_D用）
  - データレコードの特定フィールドのみ漢字変換を実施

- `src/main/java/com/example/batch/processor/VariableLengthConverter.java`
  - 可変長ファイル変換処理（FILE_E、FILE_F用）
  - BDW、RDW、電文長の再計算と制御文字変換と制御文字変換

#### ユーティリティ
- `src/main/java/com/example/batch/util/CodeConverter.java`
  - 文字コード変換Utilityクラス
  - 通常文字コード変換、漢字変換
  - ISO-2022-JP ESCシーケンス処理

### 2. 設定ファイル

- `pom.xml`
  - Maven プロジェクト定義ファイル
  - 依存ライブラリ、ビルド設定を含む

- `src/main/resources/logback.xml`
  - ログ出力設定ファイル

### 3. ドキュメント

#### 機能仕様書
- `functional-spec.adoc`
  - 詳細な機能仕様書（AsciiDoc形式）
  - 全処理の詳細仕様を記載

#### UML図
- `docs/class-diagram.puml`
  - クラス図（PlantUML形式）
  
- `docs/sequence-diagram.puml`
  - シーケンス図（PlantUML形式）

#### README
- `README.md`
  - プロジェクト概要
  - ビルド・実行方法
  - 主要クラスの説明

### 4. サンプルファイル

- `sample/parameter_file_c.properties`
  - FILE_C用サンプルパラメータファイル
  - UTF-8 → JIS混合変換の例

- `sample/parameter_file_e.properties`
  - FILE_E用サンプルパラメータファイル
  - EBCDIC → UTF-8変換の例

## セットアップ手順

### 1. アーカイブの展開

```bash
tar -xzf encoding-batch.tar.gz
cd encoding-batch
```

### 2. ビルド

```bash
mvn clean package
```

実行可能jarが `target/encoding-batch-1.0.0-jar-with-dependencies.jar` に生成されます。

### 3. 実行

```bash
java -jar target/encoding-batch-1.0.0-jar-with-dependencies.jar <パラメータファイルパス>
```

## 動作確認環境

- Java: 11以降
- Maven: 3.6以降
- OS: Linux, Windows, macOS（Java実行環境があれば動作）

## 主な機能

### サポートするファイルタイプ

1. **FILE_A、FILE_B**: 1バイト文字のみ、210バイト固定長
2. **FILE_C、FILE_D**: 1バイト・2バイト混合、380バイト固定長
3. **FILE_E、FILE_F**: 可変長レコード（BDW/RDW構造）

### サポートする文字コード

- UTF-8
- JIS X 0201（1バイト文字）
- ISO-2022-JP（2バイト文字、ESCシーケンス自動処理）
- EBCDIC (CP930/IBM930)

### 主要機能

1. **通常文字コード変換**
   - ファイル全体または指定フィールドの文字コード変換

2. **UTF-8文字数ベース処理**
   - UTF-8入力時はBufferedReaderで文字数ベース処理
   - UTF-8以外はバイト数ベース処理

3. **ISO-2022-JP ESCシーケンス処理**
   - 入力: JIS X 0208 2バイトコード（ESCシーケンスなし）
   - 自動的にESCシーケンス付与・除去を実行

4. **可変長ファイル処理**
   - BDW（ブロック長）の再計算
   - RDW（レコード長）の再計算
   - 電文長の再計算

5. **制御文字変換**
   - EBCDIC 0xB4 ⇔ JIS/UTF-8 0x74 の自動変換

6. **複数レコードタイプ対応**
   - ヘッダー、データ、トレーラー、エンドレコードに対応

## カスタマイズポイント

### FILE_C、FILE_Dの漢字項目位置

`MixedFileConverter.java` 内の以下の定義を実際の業務要件に合わせて調整してください：

```java
// データ種別=1の場合の漢字項目位置
KANJI_FIELD_DEFS.put(DATA_TYPE_1, new KanjiFieldDefinition[]{
    new KanjiFieldDefinition(50, 99),   // 位置1: 開始50、終了99
    new KanjiFieldDefinition(150, 199)  // 位置2: 開始150、終了199
});

// データ種別=2の場合の漢字項目位置
KANJI_FIELD_DEFS.put(DATA_TYPE_2, new KanjiFieldDefinition[]{
    new KanjiFieldDefinition(80, 129),  // 位置1: 開始80、終了129
    new KanjiFieldDefinition(200, 249)  // 位置2: 開始200、終了249
});
```

位置は0-basedインデックスで指定します。

## 終了コード

| コード | 説明 |
|-------|------|
| 0 | 正常終了 |
| 1 | コマンドライン引数エラー |
| 2 | パラメータ検証エラー |
| 3 | 文字コード変換エラー |
| 99 | その他の予期しないエラー |

## ログ出力

ログは以下の2箇所に出力されます：

1. 標準出力（コンソール）
2. ファイル（`encoding-batch.log`、カレントディレクトリ）

ログレベル: INFO、DEBUG

## 技術仕様

### アーキテクチャ

- **言語**: Java 11
- **ビルドツール**: Maven 3
- **ログフレームワーク**: SLF4j + Logback
- **パッケージ構成**:
  - `com.example.batch`: メインクラス
  - `com.example.batch.constant`: 定数定義
  - `com.example.batch.param`: パラメータ管理
  - `com.example.batch.processor`: 各ファイルタイプの処理クラス
  - `com.example.batch.util`: ユーティリティクラス

### 設計原則

1. **モジュール性**: ファイルタイプごとに独立した処理クラス
2. **再利用性**: 共通処理はUtilityクラスに集約
3. **保守性**: 明確なメソッド分割と詳細なログ出力
4. **拡張性**: 新しいファイルタイプの追加が容易な設計

## トラブルシューティング

### ビルドエラー

**現象**: `mvn clean package` でエラー

**対処**:
- Java 11以降がインストールされているか確認
- `JAVA_HOME` 環境変数が正しく設定されているか確認
- Mavenが正しくインストールされているか確認

### 文字コード変換エラー

**現象**: 終了コード3で終了

**対処**:
- パラメータファイルの文字コード指定を確認
- 入力ファイルの文字コードが正しいか確認
- ログファイルでエラー詳細を確認

### パラメータエラー

**現象**: 終了コード2で終了

**対処**:
- 必須パラメータが全て指定されているか確認
- ファイルIDが FILE_A～FILE_F のいずれかか確認
- 文字コード名が正しいか確認（大文字小文字、ハイフンに注意）

## サポート

質問や問題がある場合は、以下を確認してください：

1. `README.md`: 基本的な使用方法
2. `functional-spec.adoc`: 詳細な機能仕様
3. `encoding-batch.log`: 実行時のログ
4. UML図（`docs/`配下）: クラス構造とシーケンス

## 変更履歴

### Version 1.0.0 (2026-02-09)
- 初回リリース
- FILE_A～FILE_Fの全ファイルタイプに対応
- UTF-8、JIS X 0201、ISO-2022-JP、EBCDICのサポート
- ISO-2022-JP ESCシーケンス自動処理
- 可変長ファイルのBDW/RDW/電文長自動再計算
- 制御文字自動変換

---

以上
