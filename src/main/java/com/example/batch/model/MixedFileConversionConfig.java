package com.example.batch.model;

/**
 * 混合ファイル変換設定クラス。
 * 
 * <p>
 * 混合ファイル変換処理に必要なパラメータを保持する。
 * BatchParameterに依存しない汎用的な設定クラスとして定義。
 * </p>
 */
public class MixedFileConversionConfig {
    private final String sourceCharsetSingle;
    private final String sourceCharsetDouble;
    private final String targetCharsetSingle;
    private final String targetCharsetDouble;
    private final boolean convertDoubleByteCharset;
    
    /**
     * コンストラクタ。
     * 
     * @param sourceCharsetSingle 変換元1バイト文字コード
     * @param sourceCharsetDouble 変換元2バイト文字コード
     * @param targetCharsetSingle 変換先1バイト文字コード
     * @param targetCharsetDouble 変換先2バイト文字コード
     * @param convertDoubleByteCharset 漢字変換実施フラグ
     */
    public MixedFileConversionConfig(
            String sourceCharsetSingle,
            String sourceCharsetDouble,
            String targetCharsetSingle,
            String targetCharsetDouble,
            boolean convertDoubleByteCharset) {
        this.sourceCharsetSingle = sourceCharsetSingle;
        this.sourceCharsetDouble = sourceCharsetDouble;
        this.targetCharsetSingle = targetCharsetSingle;
        this.targetCharsetDouble = targetCharsetDouble;
        this.convertDoubleByteCharset = convertDoubleByteCharset;
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
    
    public boolean isConvertDoubleByteCharset() {
        return convertDoubleByteCharset;
    }
}
