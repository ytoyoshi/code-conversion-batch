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
    private boolean convertDoubleByteCharset;
    
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
        
        // 必須パラメータの取得
        param.inputFilePath = getRequiredProperty(props, "input.file.path");
        param.outputFilePath = getRequiredProperty(props, "output.file.path");
        param.sourceCharsetSingle = getRequiredProperty(props, "source.charset.single");
        param.targetCharsetSingle = getRequiredProperty(props, "target.charset.single");
        
        // file.id の取得（2バイト文字コードの必須判定に使用するため先に取得）
        String fileIdStr = getRequiredProperty(props, "file.id");
        param.fileId = FileType.fromString(fileIdStr);
        
        // 2バイト文字コード: FILE_C、FILE_D の場合のみ必須
        if (param.fileId == FileType.FILE_C || param.fileId == FileType.FILE_D) {
            param.sourceCharsetDouble = getRequiredProperty(props, "source.charset.double");
            param.targetCharsetDouble = getRequiredProperty(props, "target.charset.double");
        } else {
            param.sourceCharsetDouble = props.getProperty("source.charset.double", "").trim();
            param.targetCharsetDouble = props.getProperty("target.charset.double", "").trim();
        }
        
        // 入力ファイルの存在チェック
        if (!Files.exists(Paths.get(param.inputFilePath))) {
            throw new IllegalArgumentException("入力ファイルが存在しません: " + param.inputFilePath);
        }
        
        // 文字コードの妥当性チェック（1バイト文字コードは常にチェック）
        param.validateCharset(param.sourceCharsetSingle, "source.charset.single");
        param.validateCharset(param.targetCharsetSingle, "target.charset.single");
        
        // 文字コードの妥当性チェック（2バイト文字コードは値がある場合のみチェック）
        if (!param.sourceCharsetDouble.isEmpty()) {
            param.validateCharset(param.sourceCharsetDouble, "source.charset.double");
        }
        if (!param.targetCharsetDouble.isEmpty()) {
            param.validateCharset(param.targetCharsetDouble, "target.charset.double");
        }
        
        // サポートする文字コードのチェック（1バイト文字コードは常にチェック）
        param.validateSupportedCharset(param.sourceCharsetSingle, "source.charset.single");
        param.validateSupportedCharset(param.targetCharsetSingle, "target.charset.single");
        
        // サポートする文字コードのチェック（2バイト文字コードは値がある場合のみチェック）
        if (!param.sourceCharsetDouble.isEmpty()) {
            param.validateSupportedCharset(param.sourceCharsetDouble, "source.charset.double");
        }
        if (!param.targetCharsetDouble.isEmpty()) {
            param.validateSupportedCharset(param.targetCharsetDouble, "target.charset.double");
        }
        
        // 2バイト文字コードが変換元と変換先で異なる場合のみ漢字変換を実施するフラグを設定
        // FILE_C、FILE_D 以外は参照されないため常に false
        if (param.fileId == FileType.FILE_C || param.fileId == FileType.FILE_D) {
            param.convertDoubleByteCharset =
                !param.sourceCharsetDouble.equalsIgnoreCase(param.targetCharsetDouble);
            if (!param.convertDoubleByteCharset) {
                logger.info("2バイト文字コードが同一のため漢字変換をスキップします: {}", param.sourceCharsetDouble);
            }
        } else {
            param.convertDoubleByteCharset = false;
        }
        
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
        logger.info("変換元2バイト文字コード: {}", sourceCharsetDouble.isEmpty() ? "(未設定)" : sourceCharsetDouble);
        logger.info("変換先1バイト文字コード: {}", targetCharsetSingle);
        logger.info("変換先2バイト文字コード: {}", targetCharsetDouble.isEmpty() ? "(未設定)" : targetCharsetDouble);
        logger.info("漢字変換実施: {}", convertDoubleByteCharset);
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
    
    public boolean isConvertDoubleByteCharset() {
        return convertDoubleByteCharset;
    }
}
