package com.example.batch.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Properties;

/**
 * バッチ処理のパラメータを管理するクラス。
 * プロパティファイルからパラメータを読み込み、バリデーションを実施。
 */
public class BatchParameters {
    
    // パラメータキー定義
    private static final String KEY_INPUT_FILE = "input.file.path";
    private static final String KEY_OUTPUT_FILE = "output.file.path";
    private static final String KEY_SOURCE_CHARSET_SINGLE = "source.charset.single";
    private static final String KEY_SOURCE_CHARSET_DOUBLE = "source.charset.double";
    private static final String KEY_TARGET_CHARSET_SINGLE = "target.charset.single";
    private static final String KEY_TARGET_CHARSET_DOUBLE = "target.charset.double";
    private static final String KEY_FILE_ID = "file.id";

    // パラメータ値
    private String inputFilePath;
    private String outputFilePath;
    private String sourceCharsetSingle;
    private String sourceCharsetDouble;
    private String targetCharsetSingle;
    private String targetCharsetDouble;
    private String fileId;
    private FileType fileType;

    /**
     * プロパティファイルからパラメータを読み込む
     * 
     * @param propertyFilePath プロパティファイルのパス
     * @return 読み込んだパラメータ
     * @throws IOException ファイル読み込みエラー
     * @throws IllegalArgumentException パラメータ不正
     */
    public static BatchParameters loadFromFile(String propertyFilePath) throws IOException {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(propertyFilePath)) {
            props.load(fis);
        }

        BatchParameters params = new BatchParameters();
        params.inputFilePath = props.getProperty(KEY_INPUT_FILE);
        params.outputFilePath = props.getProperty(KEY_OUTPUT_FILE);
        params.sourceCharsetSingle = props.getProperty(KEY_SOURCE_CHARSET_SINGLE);
        params.sourceCharsetDouble = props.getProperty(KEY_SOURCE_CHARSET_DOUBLE);
        params.targetCharsetSingle = props.getProperty(KEY_TARGET_CHARSET_SINGLE);
        params.targetCharsetDouble = props.getProperty(KEY_TARGET_CHARSET_DOUBLE);
        params.fileId = props.getProperty(KEY_FILE_ID);

        params.validate();
        return params;
    }

    /**
     * パラメータのバリデーションを実施
     * 
     * @throws IllegalArgumentException バリデーションエラー
     */
    public void validate() {
        // 必須パラメータチェック
        validateNotEmpty(inputFilePath, KEY_INPUT_FILE);
        validateNotEmpty(outputFilePath, KEY_OUTPUT_FILE);
        validateNotEmpty(sourceCharsetSingle, KEY_SOURCE_CHARSET_SINGLE);
        validateNotEmpty(sourceCharsetDouble, KEY_SOURCE_CHARSET_DOUBLE);
        validateNotEmpty(targetCharsetSingle, KEY_TARGET_CHARSET_SINGLE);
        validateNotEmpty(targetCharsetDouble, KEY_TARGET_CHARSET_DOUBLE);
        validateNotEmpty(fileId, KEY_FILE_ID);

        // ファイルIDの妥当性チェック
        fileType = FileType.fromString(fileId);

        // 文字コードの妥当性チェック
        validateCharset(sourceCharsetSingle, KEY_SOURCE_CHARSET_SINGLE);
        validateCharset(sourceCharsetDouble, KEY_SOURCE_CHARSET_DOUBLE);
        validateCharset(targetCharsetSingle, KEY_TARGET_CHARSET_SINGLE);
        validateCharset(targetCharsetDouble, KEY_TARGET_CHARSET_DOUBLE);

        // サポートする文字コードの組み合わせチェック
        validateCharsetCombination();
    }

    /**
     * 空文字チェック
     */
    private void validateNotEmpty(String value, String paramName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Parameter '" + paramName + "' is required");
        }
    }

    /**
     * 文字コードの妥当性チェック
     */
    private void validateCharset(String charsetName, String paramName) {
        String normalizedName = normalizeCharsetName(charsetName);
        try {
            Charset.forName(normalizedName);
        } catch (UnsupportedCharsetException e) {
            throw new IllegalArgumentException(
                "Unsupported charset for '" + paramName + "': " + charsetName, e);
        }
    }

    /**
     * 文字コードの組み合わせチェック
     */
    private void validateCharsetCombination() {
        // サポートする文字コード: UTF-8, JIS(JIS_X0201/ISO-2022-JP), EBCDIC(CP930)
        validateSupportedCharset(sourceCharsetSingle, "source.charset.single");
        validateSupportedCharset(sourceCharsetDouble, "source.charset.double");
        validateSupportedCharset(targetCharsetSingle, "target.charset.single");
        validateSupportedCharset(targetCharsetDouble, "target.charset.double");
    }

    /**
     * サポートする文字コードかチェック
     */
    private void validateSupportedCharset(String charsetName, String paramName) {
        String normalized = normalizeCharsetName(charsetName);
        boolean isSupported = 
            normalized.equals("UTF-8") ||
            normalized.equals("ISO-2022-JP") ||
            normalized.equals("JIS_X0201") ||
            normalized.equals("CP930") ||
            normalized.equals("IBM930");
        
        if (!isSupported) {
            throw new IllegalArgumentException(
                "Charset '" + charsetName + "' for parameter '" + paramName + 
                "' is not supported. Supported charsets: UTF-8, ISO-2022-JP, JIS_X0201, CP930");
        }
    }

    /**
     * 文字コード名を正規化
     */
    private String normalizeCharsetName(String charsetName) {
        String normalized = charsetName.trim().toUpperCase();
        
        // エイリアスを標準名に変換
        if (normalized.equals("JIS") || normalized.equals("JIS_X_0201")) {
            return "JIS_X0201";
        }
        if (normalized.equals("EBCDIC") || normalized.equals("EBCDIC_SBCS") || 
            normalized.equals("EBCDIC_DBCS")) {
            return "CP930";
        }
        
        return normalized;
    }

    /**
     * 変換元1バイト文字のCharsetを取得
     */
    public Charset getSourceCharsetSingle() {
        return Charset.forName(normalizeCharsetName(sourceCharsetSingle));
    }

    /**
     * 変換元2バイト文字のCharsetを取得
     */
    public Charset getSourceCharsetDouble() {
        return Charset.forName(normalizeCharsetName(sourceCharsetDouble));
    }

    /**
     * 変換先1バイト文字のCharsetを取得
     */
    public Charset getTargetCharsetSingle() {
        return Charset.forName(normalizeCharsetName(targetCharsetSingle));
    }

    /**
     * 変換先2バイト文字のCharsetを取得
     */
    public Charset getTargetCharsetDouble() {
        return Charset.forName(normalizeCharsetName(targetCharsetDouble));
    }

    // Getters
    public String getInputFilePath() {
        return inputFilePath;
    }

    public String getOutputFilePath() {
        return outputFilePath;
    }

    public String getFileId() {
        return fileId;
    }

    public FileType getFileType() {
        return fileType;
    }

    public String getSourceCharsetSingleName() {
        return sourceCharsetSingle;
    }

    public String getSourceCharsetDoubleName() {
        return sourceCharsetDouble;
    }

    public String getTargetCharsetSingleName() {
        return targetCharsetSingle;
    }

    public String getTargetCharsetDoubleName() {
        return targetCharsetDouble;
    }
}
