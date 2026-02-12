package com.example.batch.processor;

import com.example.batch.model.MixedFileConversionConfig;
import com.example.batch.param.BatchParameter;
import com.example.batch.util.FileConversionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 混合ファイル変換処理クラス（FILE_C、FILE_D用）。
 * 
 * <p>
 * 1バイト文字と2バイト文字が混合するファイルを変換する。
 * データレコードの特定フィールドのみ漢字変換を実施する。
 * 実際の変換ロジックはFileConversionUtilに委譲する。
 * </p>
 */
public class MixedFileConverter {
    private static final Logger logger = LoggerFactory.getLogger(MixedFileConverter.class);
    
    private final BatchParameter param;
    
    /**
     * コンストラクタ。
     * 
     * @param param バッチパラメータ
     */
    public MixedFileConverter(BatchParameter param) {
        this.param = param;
    }
    
    /**
     * 混合ファイル変換処理を実行する。
     * 
     * @throws IOException ファイル入出力エラー
     */
    public void execute() throws IOException {
        logger.info("========== 混合ファイル変換処理開始 ==========");
        logger.info("ファイルタイプ: {}", param.getFileId());
        logger.info("入力ファイル: {}", param.getInputFilePath());
        logger.info("出力ファイル: {}", param.getOutputFilePath());
        logger.info("変換元1バイト文字コード: {}", param.getSourceCharsetSingle());
        logger.info("変換元2バイト文字コード: {}", param.getSourceCharsetDouble());
        logger.info("変換先1バイト文字コード: {}", param.getTargetCharsetSingle());
        logger.info("変換先2バイト文字コード: {}", param.getTargetCharsetDouble());
        
        // 入力ファイル全体を読み込む
        byte[] inputBytes = readInputFile();
        logger.info("入力ファイル読み込み完了: {} bytes", inputBytes.length);
        
        // 変換設定を作成
        MixedFileConversionConfig config = new MixedFileConversionConfig(
            param.getSourceCharsetSingle(),
            param.getSourceCharsetDouble(),
            param.getTargetCharsetSingle(),
            param.getTargetCharsetDouble(),
            param.isConvertDoubleByteCharset()
        );
        
        // 文字コード変換実行（FileConversionUtilに委譲）
        byte[] outputBytes = FileConversionUtil.convertMixedFile(inputBytes, config);
        logger.info("文字コード変換完了: {} bytes -> {} bytes", 
            inputBytes.length, outputBytes.length);
        
        // 出力ファイルに書き込む
        writeOutputFile(outputBytes);
        logger.info("出力ファイル書き込み完了: {} bytes", outputBytes.length);
        
        logger.info("========== 混合ファイル変換処理完了 ==========");
    }
    
    /**
     * 入力ファイル全体を読み込む。
     * 
     * @return ファイル全体のバイト配列
     * @throws IOException ファイル読み込みエラー
     */
    private byte[] readInputFile() throws IOException {
        return Files.readAllBytes(Paths.get(param.getInputFilePath()));
    }
    
    /**
     * 変換後のバイト配列を出力ファイルに書き込む。
     * 
     * @param outputBytes 出力バイト配列
     * @throws IOException ファイル書き込みエラー
     */
    private void writeOutputFile(byte[] outputBytes) throws IOException {
        Files.write(Paths.get(param.getOutputFilePath()), outputBytes);
    }
}
