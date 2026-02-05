package com.example.batch;

import com.example.batch.config.BatchParameters;
import com.example.batch.config.FileType;
import com.example.batch.processor.FileABProcessor;
import com.example.batch.processor.FileCDProcessor;
import com.example.batch.processor.FileEFProcessor;
import com.example.batch.processor.RecordProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.CharacterCodingException;

/**
 * 文字コード変換バッチのメインクラス。
 * 
 * 実行方法:
 *   java -jar encoding-batch.jar <parameter-file-path>
 * 
 * パラメータファイル形式(key=value):
 *   input.file.path=/path/to/input.dat
 *   output.file.path=/path/to/output.dat
 *   source.charset.single=UTF-8
 *   source.charset.double=UTF-8
 *   target.charset.single=JIS_X_0201
 *   target.charset.double=ISO-2022-JP
 *   file.id=FILE_C
 */
public class EncodingBatchMain {

    private static final Logger LOGGER = LoggerFactory.getLogger(EncodingBatchMain.class);

    /**
     * メインメソッド
     * 
     * @param args コマンドライン引数 [0]: パラメータファイルパス
     */
    public static void main(String[] args) {
        
        LOGGER.info("=== Encoding Batch Started ===");
        
        try {
            // コマンドライン引数チェック
            if (args.length < 1) {
                LOGGER.error("Usage: java -jar encoding-batch.jar <parameter-file-path>");
                System.exit(1);
            }

            String paramFilePath = args[0];
            LOGGER.info("Parameter file: {}", paramFilePath);

            // パラメータ読み込み
            BatchParameters parameters = loadParameters(paramFilePath);
            LOGGER.info("Parameters loaded successfully");

            // パラメータ検証
            validateParameters(parameters);
            LOGGER.info("Parameters validated successfully");

            // ファイル処理
            processFile(parameters);
            
            LOGGER.info("=== Encoding Batch Completed Successfully ===");
            System.exit(0);

        } catch (IllegalArgumentException e) {
            LOGGER.error("Parameter validation error: {}", e.getMessage());
            System.exit(2);
            
        } catch (CharacterCodingException e) {
            LOGGER.error("Character coding error occurred. Check the log for details.");
            System.exit(3);
            
        } catch (Exception e) {
            LOGGER.error("Unexpected error occurred", e);
            System.exit(99);
        }
    }

    /**
     * パラメータファイルを読み込む
     * 
     * @param paramFilePath パラメータファイルパス
     * @return バッチパラメータ
     * @throws Exception 読み込みエラー
     */
    private static BatchParameters loadParameters(String paramFilePath) throws Exception {
        try {
            return BatchParameters.loadFromFile(paramFilePath);
        } catch (Exception e) {
            LOGGER.error("Failed to load parameters from file: {}", paramFilePath, e);
            throw e;
        }
    }

    /**
     * パラメータの妥当性を検証
     * 
     * @param parameters バッチパラメータ
     * @throws IllegalArgumentException 検証エラー
     */
    private static void validateParameters(BatchParameters parameters) {
        LOGGER.info("Validating parameters...");
        LOGGER.info("  File ID: {}", parameters.getFileId());
        LOGGER.info("  Input file: {}", parameters.getInputFilePath());
        LOGGER.info("  Output file: {}", parameters.getOutputFilePath());
        LOGGER.info("  Source charset: single={}, double={}", 
                   parameters.getSourceCharsetSingleName(),
                   parameters.getSourceCharsetDoubleName());
        LOGGER.info("  Target charset: single={}, double={}", 
                   parameters.getTargetCharsetSingleName(),
                   parameters.getTargetCharsetDoubleName());
    }

    /**
     * ファイル処理を実行
     * 
     * @param parameters バッチパラメータ
     * @throws Exception 処理エラー
     */
    private static void processFile(BatchParameters parameters) throws Exception {
        
        // ファイルタイプに応じたプロセッサを選択
        RecordProcessor processor = selectProcessor(parameters);
        
        LOGGER.info("Starting file processing...");
        
        // ファイル処理実行
        processor.processFile(
            parameters.getInputFilePath(),
            parameters.getOutputFilePath()
        );
        
        LOGGER.info("File processing completed");
    }

    /**
     * ファイルタイプに応じた適切なプロセッサを選択
     * 
     * @param parameters バッチパラメータ
     * @return レコードプロセッサ
     */
    private static RecordProcessor selectProcessor(BatchParameters parameters) {
        
        FileType fileType = parameters.getFileType();
        LOGGER.info("Selecting processor for file type: {}", fileType);

        RecordProcessor processor;

        switch (fileType) {
            case FILE_A:
            case FILE_B:
                LOGGER.info("Using FileABProcessor for simple whole-file conversion");
                processor = new FileABProcessor(parameters, fileType);
                break;

            case FILE_C:
            case FILE_D:
                LOGGER.info("Using FileCDProcessor for mixed encoding field conversion");
                processor = new FileCDProcessor(parameters, fileType);
                break;

            case FILE_E:
            case FILE_F:
                LOGGER.info("Using FileEFProcessor for variable length record conversion");
                processor = new FileEFProcessor(parameters, fileType);
                break;

            default:
                throw new IllegalArgumentException("Unsupported file type: " + fileType);
        }

        return processor;
    }
}
