package com.example.batch.processor;

import com.example.batch.param.BatchParameter;
import com.example.batch.util.FileConversionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 全体変換処理クラス（FILE_A、FILE_B用）。
 * 
 * <p>
 * 1バイト文字のみのファイルを一括変換する。
 * 実際の変換ロジックはFileConversionUtilに委譲する。
 * </p>
 */
public class WholeFileConverter {
    private static final Logger logger = LoggerFactory.getLogger(WholeFileConverter.class);
    
    private final BatchParameter param;
    
    /**
     * コンストラクタ。
     * 
     * @param param バッチパラメータ
     */
    public WholeFileConverter(BatchParameter param) {
        this.param = param;
    }
    
    /**
     * 全体変換処理を実行する。
     * 
     * @throws IOException ファイル入出力エラー
     */
    public void execute() throws IOException {
        logger.info("========== 全体変換処理開始 ==========");
        logger.info("ファイルタイプ: {}", param.getFileId());
        logger.info("入力ファイル: {}", param.getInputFilePath());
        logger.info("出力ファイル: {}", param.getOutputFilePath());
        logger.info("変換元文字コード: {}", param.getSourceCharsetSingle());
        logger.info("変換先文字コード: {}", param.getTargetCharsetSingle());
        
        // 入力ファイル全体を読み込む
        byte[] inputBytes = readInputFile();
        logger.info("入力ファイル読み込み完了: {} bytes", inputBytes.length);
        
        // 文字コード変換実行（FileConversionUtilに委譲）
        byte[] outputBytes = FileConversionUtil.convertWholeFile(
            inputBytes,
            param.getSourceCharsetSingle(),
            param.getTargetCharsetSingle()
        );
        logger.info("文字コード変換完了: {} bytes -> {} bytes", 
            inputBytes.length, outputBytes.length);
        
        // 出力ファイルに書き込む
        writeOutputFile(outputBytes);
        logger.info("出力ファイル書き込み完了: {} bytes", outputBytes.length);
        
        logger.info("========== 全体変換処理完了 ==========");
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
