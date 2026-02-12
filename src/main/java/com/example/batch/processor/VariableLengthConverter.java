package com.example.batch.processor;

import com.example.batch.param.BatchParameter;
import com.example.batch.util.FileConversionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 可変長ファイル変換処理クラス（FILE_E、FILE_F用）。
 * 
 * <p>
 * BDW、RDW構造を持つ可変長ファイルを変換する。
 * 電文長の再計算とBDW/RDWの更新を行う。
 * 実際の変換ロジックはFileConversionUtilに委譲する。
 * </p>
 */
public class VariableLengthConverter {
    private static final Logger logger = LoggerFactory.getLogger(VariableLengthConverter.class);
    
    private final BatchParameter param;
    
    /**
     * コンストラクタ。
     * 
     * @param param バッチパラメータ
     */
    public VariableLengthConverter(BatchParameter param) {
        this.param = param;
    }
    
    /**
     * 可変長ファイル変換処理を実行する。
     * 
     * @throws IOException ファイル入出力エラー
     */
    public void execute() throws IOException {
        logger.info("========== 可変長ファイル変換処理開始 ==========");
        logger.info("ファイルタイプ: {}", param.getFileId());
        logger.info("入力ファイル: {}", param.getInputFilePath());
        logger.info("出力ファイル: {}", param.getOutputFilePath());
        logger.info("変換元文字コード: {}", param.getSourceCharsetSingle());
        logger.info("変換先文字コード: {}", param.getTargetCharsetSingle());
        
        // 入力ファイル全体を読み込む
        byte[] inputBytes = readInputFile();
        logger.info("入力ファイル読み込み完了: {} bytes", inputBytes.length);
        
        // 文字コード変換実行（FileConversionUtilに委譲）
        byte[] outputBytes = FileConversionUtil.convertVariableLengthFile(
            inputBytes,
            param.getSourceCharsetSingle(),
            param.getTargetCharsetSingle()
        );
        logger.info("文字コード変換完了: {} bytes -> {} bytes", 
            inputBytes.length, outputBytes.length);
        
        // 出力ファイルに書き込む
        writeOutputFile(outputBytes);
        logger.info("出力ファイル書き込み完了: {} bytes", outputBytes.length);
        
        logger.info("========== 可変長ファイル変換処理完了 ==========");
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
