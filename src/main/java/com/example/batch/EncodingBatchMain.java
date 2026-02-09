package com.example.batch;

import com.example.batch.constant.FileType;
import com.example.batch.param.BatchParameter;
import com.example.batch.processor.MixedFileConverter;
import com.example.batch.processor.VariableLengthConverter;
import com.example.batch.processor.WholeFileConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文字コード変換バッチのメインクラス。
 * 
 * <p>
 * 指定されたファイルの文字コード変換を実行する。
 * </p>
 */
public class EncodingBatchMain {
    private static final Logger logger = LoggerFactory.getLogger(EncodingBatchMain.class);
    
    // 終了コード定数
    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_ARGS_ERROR = 1;
    private static final int EXIT_CODE_PARAM_ERROR = 2;
    private static final int EXIT_CODE_CONVERSION_ERROR = 3;
    private static final int EXIT_CODE_UNEXPECTED_ERROR = 99;
    
    /**
     * メインメソッド。
     * 
     * @param args コマンドライン引数（パラメータファイルパス）
     */
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("文字コード変換バッチ開始");
        logger.info("========================================");
        
        int exitCode = execute(args);
        
        logger.info("========================================");
        logger.info("文字コード変換バッチ終了: 終了コード={}", exitCode);
        logger.info("========================================");
        
        System.exit(exitCode);
    }
    
    /**
     * バッチ処理を実行する。
     * 
     * @param args コマンドライン引数
     * @return 終了コード
     */
    private static int execute(String[] args) {
        try {
            // コマンドライン引数チェック
            if (args.length != 1) {
                logger.error("コマンドライン引数エラー: パラメータファイルパスを指定してください");
                logger.error("使用方法: java -jar encoding-batch-1.0.0-jar-with-dependencies.jar <パラメータファイルパス>");
                return EXIT_CODE_ARGS_ERROR;
            }
            
            String parameterFilePath = args[0];
            logger.info("パラメータファイルパス: {}", parameterFilePath);
            
            // パラメータファイル読み込みとバリデーション
            BatchParameter param;
            try {
                param = BatchParameter.load(parameterFilePath);
            } catch (IllegalArgumentException e) {
                logger.error("パラメータ検証エラー: {}", e.getMessage());
                return EXIT_CODE_PARAM_ERROR;
            } catch (Exception e) {
                logger.error("パラメータ読み込みエラー", e);
                return EXIT_CODE_PARAM_ERROR;
            }
            
            // ファイルIDに応じた処理を実行
            FileType fileId = param.getFileId();
            logger.info("処理対象ファイルタイプ: {}", fileId);
            
            try {
                switch (fileId) {
                    case FILE_A:
                    case FILE_B:
                        executeWholeFileConversion(param);
                        break;
                        
                    case FILE_C:
                    case FILE_D:
                        executeMixedFileConversion(param);
                        break;
                        
                    case FILE_E:
                    case FILE_F:
                        executeVariableLengthConversion(param);
                        break;
                        
                    default:
                        logger.error("未サポートのファイルタイプ: {}", fileId);
                        return EXIT_CODE_PARAM_ERROR;
                }
            } catch (Exception e) {
                logger.error("文字コード変換エラー", e);
                return EXIT_CODE_CONVERSION_ERROR;
            }
            
            logger.info("処理が正常に完了しました");
            return EXIT_CODE_SUCCESS;
            
        } catch (Exception e) {
            logger.error("予期しないエラーが発生しました", e);
            return EXIT_CODE_UNEXPECTED_ERROR;
        }
    }
    
    /**
     * 全体変換処理を実行する（FILE_A、FILE_B用）。
     * 
     * @param param バッチパラメータ
     * @throws Exception 処理エラー
     */
    private static void executeWholeFileConversion(BatchParameter param) throws Exception {
        logger.info("全体変換処理を開始します");
        WholeFileConverter converter = new WholeFileConverter(param);
        converter.execute();
        logger.info("全体変換処理が完了しました");
    }
    
    /**
     * 混合ファイル変換処理を実行する（FILE_C、FILE_D用）。
     * 
     * @param param バッチパラメータ
     * @throws Exception 処理エラー
     */
    private static void executeMixedFileConversion(BatchParameter param) throws Exception {
        logger.info("混合ファイル変換処理を開始します");
        MixedFileConverter converter = new MixedFileConverter(param);
        converter.execute();
        logger.info("混合ファイル変換処理が完了しました");
    }
    
    /**
     * 可変長ファイル変換処理を実行する（FILE_E、FILE_F用）。
     * 
     * @param param バッチパラメータ
     * @throws Exception 処理エラー
     */
    private static void executeVariableLengthConversion(BatchParameter param) throws Exception {
        logger.info("可変長ファイル変換処理を開始します");
        VariableLengthConverter converter = new VariableLengthConverter(param);
        converter.execute();
        logger.info("可変長ファイル変換処理が完了しました");
    }
}
