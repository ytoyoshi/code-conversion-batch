package com.example.batch.param;

import com.example.batch.constant.FileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * 処理指示パラメータを保持するクラス。
 * 
 * <p>
 * パラメータファイルの読み込みとバリデーションを実行する。
 * </p>
 */
public class BatchParameter {
    private static final Logger logger = LoggerFactory.getLogger(BatchParameter.class);
    
    // サポートする文字コード
    private static final Set<String> SUPPORTED_CHARSETS = new HashSet<>(Arrays.asList(
        "UTF-8",
        "ISO-2022-JP",
        "JIS_X0201",
        "CP930",
        "IBM930"  // CP930のエイリアス
    ));
    
    private String inputFilePath;
    private String outputFilePath;
    private String sourceCharsetSingle;
    private String sourceCharsetDouble;
    private String targetCharsetSingle;
    private String targetCharsetDouble;
    private FileType fileId;
    
    /**
     * パラメータファイルから処理指示パラメータを読み込む。
     * 
     * @param parameterFilePath パラメータファイルパス
     * @return BatchParameter インスタンス
     * @throws IOException ファイル読み込みエラー
     * @throws IllegalArgumentException パラメータ検証エラー
     */
    public static BatchParameter load(String parameterFilePath) throws IOException {
        logger.info("パラメータファイル読み込み開始: {}", parameterFilePath);
        
        // パラメータファイルの存在チェック
        if (!Files.exists(Paths.get(parameterFilePath))) {
            throw new IllegalArgumentException("パラメータファイルが存在しません: " + parameterFilePath);
        }
        
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(parameterFilePath)) {
            props.load(fis);
        }
        
        BatchParameter param = new BatchParameter();
        
        // 必須パラメータの取得とバリデーション
        param.inputFilePath = getRequiredProperty(props, "input.file.path");
        param.outputFilePath = getRequiredProperty(props, "output.file.path");
        param.sourceCharsetSingle = getRequiredProperty(props, "source.charset.single");
        param.sourceCharsetDouble = getRequiredProperty(props, "source.charset.double");
        param.targetCharsetSingle = getRequiredProperty(props, "target.charset.single");
        param.targetCharsetDouble = getRequiredProperty(props, "target.charset.double");
        
        String fileIdStr = getRequiredProperty(props, "file.id");
        param.fileId = FileType.fromString(fileIdStr);
        
        // 入力ファイルの存在チェック
        if (!Files.exists(Paths.get(param.inputFilePath))) {
            throw new IllegalArgumentException("入力ファイルが存在しません: " + param.inputFilePath);
        }
        
        // 文字コードの妥当性チェック
        param.validateCharset(param.sourceCharsetSingle, "source.charset.single");
        param.validateCharset(param.sourceCharsetDouble, "source.charset.double");
        param.validateCharset(param.targetCharsetSingle, "target.charset.single");
        param.validateCharset(param.targetCharsetDouble, "target.charset.double");
        
        // サポートする文字コードの組み合わせチェック
        param.validateSupportedCharset(param.sourceCharsetSingle, "source.charset.single");
        param.validateSupportedCharset(param.sourceCharsetDouble, "source.charset.double");
        param.validateSupportedCharset(param.targetCharsetSingle, "target.charset.single");
        param.validateSupportedCharset(param.targetCharsetDouble, "target.charset.double");
        
        // パラメータ内容をログ出力
        param.logParameters();
        
        logger.info("パラメータファイル読み込み完了");
        return param;
    }
    
    /**
     * 必須プロパティを取得する。
     * 
     * @param props Properties
     * @param key プロパティキー
     * @return プロパティ値
     * @throws IllegalArgumentException 値が存在しないまたは空の場合
     */
    private static String getRequiredProperty(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("必須パラメータが指定されていません: " + key);
        }
        return value.trim();
    }
    
    /**
     * 文字コードの妥当性をチェックする。
     * 
     * @param charsetName 文字コード名
     * @param paramName パラメータ名（エラーメッセージ用）
     * @throws UnsupportedCharsetException サポートされていない文字コードの場合
     */
    private void validateCharset(String charsetName, String paramName) {
        try {
            Charset.forName(charsetName);
        } catch (UnsupportedCharsetException e) {
            throw new UnsupportedCharsetException(
                paramName + " に指定された文字コードはサポートされていません: " + charsetName);
        }
    }
    
    /**
     * サポートする文字コードかチェックする。
     * 
     * @param charsetName 文字コード名
     * @param paramName パラメータ名（エラーメッセージ用）
     * @throws IllegalArgumentException サポート対象外の文字コードの場合
     */
    private void validateSupportedCharset(String charsetName, String paramName) {
        String normalizedName = charsetName.toUpperCase().replace("-", "").replace("_", "");
        
        boolean isSupported = SUPPORTED_CHARSETS.stream()
            .anyMatch(supported -> supported.toUpperCase().replace("-", "").replace("_", "")
                .equals(normalizedName));
        
        if (!isSupported) {
            throw new IllegalArgumentException(
                paramName + " に指定された文字コードはサポート対象外です: " + charsetName +
                " (サポート対象: UTF-8, ISO-2022-JP, JIS_X0201, CP930)");
        }
    }
    
    /**
     * パラメータ内容をログ出力する。
     */
    private void logParameters() {
        logger.info("========== パラメータ内容 ==========");
        logger.info("ファイルID: {}", fileId);
        logger.info("入力ファイルパス: {}", inputFilePath);
        logger.info("出力ファイルパス: {}", outputFilePath);
        logger.info("変換元1バイト文字コード: {}", sourceCharsetSingle);
        logger.info("変換元2バイト文字コード: {}", sourceCharsetDouble);
        logger.info("変換先1バイト文字コード: {}", targetCharsetSingle);
        logger.info("変換先2バイト文字コード: {}", targetCharsetDouble);
        logger.info("====================================");
    }
    
    // Getters
    
    public String getInputFilePath() {
        return inputFilePath;
    }
    
    public String getOutputFilePath() {
        return outputFilePath;
    }
    
    public String getSourceCharsetSingle() {
        return sourceCharsetSingle;
    }
    
    public String getSourceCharsetDouble() {
        return sourceCharsetDouble;
    }
    
    public String getTargetCharsetSingle() {
        return targetCharsetSingle;
    }
    
    public String getTargetCharsetDouble() {
        return targetCharsetDouble;
    }
    
    public FileType getFileId() {
        return fileId;
    }
}
