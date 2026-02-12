# 文字コード変換バッチアプリケーション

メインフレームシステム連携における文字コード変換を行うJavaバッチアプリケーション。

## 概要

本アプリケーションは、指定された入力ファイルを読み込み、パラメータで指定された変換先文字コードへ変換を行い、変換後データを出力するバッチアプリケーションです。

### 主な機能

- UTF-8、JIS X 0201、ISO-2022-JP、EBCDIC (CP930) 間の文字コード変換
- 固定長ファイル（FILE_A～FILE_D）と可変長ファイル（FILE_E～FILE_F）に対応
- 混合文字コード（1バイト文字と2バイト文字の混在）の選択的変換
- ISO-2022-JPのESCシーケンス自動処理
- 可変長ファイルのBDW/RDW/電文長の自動再計算
- 制御文字（終端符号）の自動変換

## システム要件

- Java 11 以降
- Maven 3.6 以降

## プロジェクト構成

```
encoding-batch/
├── pom.xml                              # Mavenプロジェクト定義
├── functional-spec.adoc                 # 機能仕様書
├── README.md                            # 本ファイル
├── src/
│   └── main/
│       ├── java/com/example/batch/
│       │   ├── EncodingBatchMain.java          # メイン処理
│       │   ├── constant/
│       │   │   └── FileType.java               # ファイルタイプEnum
│       │   ├── param/
│       │   │   └── BatchParameter.java         # パラメータ管理
│       │   ├── processor/
│       │   │   ├── WholeFileConverter.java     # 全体変換処理
│       │   │   ├── MixedFileConverter.java     # 混合ファイル変換
│       │   │   └── VariableLengthConverter.java # 可変長変換
│       │   └── util/
│       │       └── CodeConverter.java          # 文字コード変換Util
│       └── resources/
│           └── logback.xml                     # ログ設定
├── docs/
│   ├── class-diagram.puml                      # クラス図
│   └── sequence-diagram.puml                   # シーケンス図
└── sample/
    ├── parameter_file_c.properties             # サンプルパラメータ（FILE_C）
    └── parameter_file_e.properties             # サンプルパラメータ（FILE_E）
```

## ビルド方法

```bash
# プロジェクトルートで実行
mvn clean package

# 実行可能jarが生成されます
# target/encoding-batch-1.0.0-jar-with-dependencies.jar
```

## 実行方法

### 基本コマンド

```bash
java -jar encoding-batch-1.0.0-jar-with-dependencies.jar <パラメータファイルパス>
```

### パラメータファイル

処理指示パラメータファイル（.properties形式、UTF-8エンコーディング）を用意します。

#### パラメータ項目

| パラメータ名 | 必須 | 説明 |
|-------------|------|------|
| input.file.path | ○ | 入力ファイルのフルパス |
| output.file.path | ○ | 出力ファイルのフルパス |
| source.charset.single | ○ | 変換元1バイト文字コード |
| source.charset.double | ○ | 変換元2バイト文字コード |
| target.charset.single | ○ | 変換先1バイト文字コード |
| target.charset.double | ○ | 変換先2バイト文字コード |
| file.id | ○ | ファイル識別子（FILE_A～FILE_F） |

#### パラメータファイル例

**FILE_C（UTF-8 → JIS混合）の場合:**

```properties
input.file.path=/data/in/file_c_input.txt
output.file.path=/data/out/file_c_output.txt
source.charset.single=UTF-8
source.charset.double=UTF-8
target.charset.single=JIS_X0201
target.charset.double=ISO-2022-JP
file.id=FILE_C
```

**FILE_E（EBCDIC → UTF-8）の場合:**

```properties
input.file.path=/data/in/file_e_input.dat
output.file.path=/data/out/file_e_output.dat
source.charset.single=Cp930
source.charset.double=Cp930
target.charset.single=UTF-8
target.charset.double=UTF-8
file.id=FILE_E
```

## サポートするファイルタイプ

| ファイルID | レコード形式 | レコード長 | 特徴 |
|-----------|------------|-----------|------|
| FILE_A | 固定長 | 210 bytes | 1バイト文字のみ（ヘッダーに6バイト目コード区分あり） |
| FILE_B | 固定長 | 210 bytes | 1バイト文字のみ（ヘッダーに6バイト目コード区分あり） |
| FILE_C | 固定長 | 380 bytes | 1バイト・2バイト混合<br>UTF-8の場合は文字数ベース処理<br>ヘッダーに6バイト目コード区分あり |
| FILE_D | 固定長 | 380 bytes | 1バイト・2バイト混合<br>UTF-8の場合は文字数ベース処理<br>ヘッダーに6バイト目コード区分あり |
| FILE_E | 可変長 | - | BDW/RDW構造 |
| FILE_F | 可変長 | - | BDW/RDW構造 |

## サポートする文字コード

| 文字コード | Java Charset名 | 備考 |
|-----------|---------------|------|
| UTF-8 | UTF-8 | Unicode標準 |
| JIS X 0201 | JIS_X0201 | 1バイト文字 |
| ISO-2022-JP | ISO-2022-JP | 2バイト文字（ESCシーケンス処理あり） |
| EBCDIC | Cp930 (IBM930) | メインフレーム用 |

## 終了コード

| 終了コード | 説明 |
|-----------|------|
| 0 | 正常終了 |
| 1 | コマンドライン引数エラー |
| 2 | パラメータ検証エラー |
| 3 | 文字コード変換エラー |
| 99 | その他の予期しないエラー |

## ログ出力

ログは以下の2箇所に出力されます：

- 標準出力（コンソール）
- ファイル（カレントディレクトリの `encoding-batch.log`）

## 主要クラス

### EncodingBatchMain
メイン処理クラス。パラメータ読み込み、ファイルタイプ判定、適切な変換処理の呼び出しを行います。

### BatchParameter
処理指示パラメータを管理するクラス。パラメータファイルの読み込み、バリデーション、漢字変換フラグの設定を行います。

### FileConversionUtil
**ファイル変換ロジックを集約したUtilityクラス（新規）。**

全てのファイルタイプの変換ロジックを提供します。各Converterクラスから呼び出されます。

**公開メソッド:**
- `convertWholeFile()`: 全体変換（FILE_A/B用）
- `convertMixedFile()`: 混合ファイル変換（FILE_C/D用）
- `convertVariableLengthFile()`: 可変長ファイル変換（FILE_E/F用）

**特徴:**
- 入出力処理を含まず、バイト配列の変換のみを担当
- ヘッダーレコードのコード区分自動更新機能を内包
- BDW/RDW再計算、制御文字変換など全ての変換ロジックを統合

### CodeConverter
文字コード変換を行うUtilityクラス。

- `convertCharset()`: 通常の文字コード変換
- `convertKanjiCharset()`: 漢字の文字コード変換（ISO-2022-JP ESCシーケンス処理含む）

### WholeFileConverter
FILE_A、FILE_B用の変換処理クラス。

**役割:**
- ファイル読み込み
- FileConversionUtilへの変換委譲
- ファイル書き込み

### MixedFileConverter
FILE_C、FILE_D用の変換処理クラス。

**役割:**
- ファイル読み込み
- MixedFileConversionConfig作成
- FileConversionUtilへの変換委譲
- ファイル書き込み

### VariableLengthConverter
FILE_E、FILE_F用の変換処理クラス。

**役割:**
- ファイル読み込み
- FileConversionUtilへの変換委譲
- ファイル書き込み

### MixedFileConversionConfig
混合ファイル変換の設定を保持するクラス（新規）。

BatchParameterに依存しない汎用的な設計で、変換に必要なパラメータをカプセル化します。

### KanjiFieldDefinition
漢字項目の位置定義を保持するクラス（新規）。

UTF-8の場合は文字位置、JIS/EBCDICの場合はバイト位置として解釈されます。

## ドキュメント

- **機能仕様書**: `functional-spec.adoc`
- **クラス図**: `docs/class-diagram.puml`
- **シーケンス図**: `docs/sequence-diagram.puml`

PlantUMLファイルは、PlantUMLツールまたはオンラインエディタで表示できます。

## 注意事項

### FILE_C、FILE_Dの漢字項目位置

`MixedFileConverter`クラス内の漢字項目定義は仮の値です。実際の業務要件に合わせて以下の箇所を調整してください：

```java
// データ種別=1の場合の漢字項目位置
KANJI_FIELD_DEFS.put(DATA_TYPE_1, new KanjiFieldDefinition[]{
    new KanjiFieldDefinition(50, 99),   // 位置1
    new KanjiFieldDefinition(150, 199)  // 位置2
});

// データ種別=2の場合の漢字項目位置
KANJI_FIELD_DEFS.put(DATA_TYPE_2, new KanjiFieldDefinition[]{
    new KanjiFieldDefinition(80, 129),  // 位置1
    new KanjiFieldDefinition(200, 249)  // 位置2
});
```

### ISO-2022-JPの処理

- 入力: JIS X 0208の2バイトコードそのまま（ESCシーケンスなし）
- 処理: 自動的にESCシーケンスを付与してISO-2022-JPエンコード
- 出力: ESCシーケンスを除去して2バイトコードのみ返却

## ライセンス

（プロジェクトのライセンスを記載）

## 作成者

（作成者情報を記載）
