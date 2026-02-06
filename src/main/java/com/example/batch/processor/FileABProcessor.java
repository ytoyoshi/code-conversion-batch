package com.example.batch.processor;

import com.example.batch.config.BatchParameters;
import com.example.batch.config.FileType;
import com.example.batch.util.CodeConverter;

import java.io.*;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * FILE_A, FILE_B用のファイルプロセッサ。
 * 1バイト文字のみで構成されるファイルを全体一括変換。
 */
public class FileABProcessor extends RecordProcessor {

    /**
     * コンストラクタ
     * 
     * @param parameters バッチパラメータ
     * @param fileType ファイルタイプ(FILE_A または FILE_B)
     */
    public FileABProcessor(BatchParameters parameters, FileType fileType) {
        super(parameters, fileType);
        
        if (fileType != FileType.FILE_A && fileType != FileType.FILE_B) {
            throw new IllegalArgumentException(
                "FileABProcessor only supports FILE_A and FILE_B, but got: " + fileType);
        }
    }

    /**
     * ファイル全体を一括変換。
     * レコード単位の処理は行わず、ファイル全体を読み込んで変換する。
     * 
     * @param inputPath 入力ファイルパス
     * @param outputPath 出力ファイルパス
     * @throws IOException ファイルI/Oエラー
     * @throws CharacterCodingException 文字コード変換エラー
     */
    @Override
    public void processFile(String inputPath, String outputPath) 
            throws IOException, CharacterCodingException {
        
        LOGGER.info("Processing file (whole file conversion): {} -> {}", inputPath, outputPath);
        LOGGER.info("File type: {}", fileType);
        LOGGER.info("Source charset: {}", parameters.getSourceCharsetSingleName());
        LOGGER.info("Target charset: {}", parameters.getTargetCharsetSingleName());

        try {
            // ファイル全体を読み込み
            byte[] inputData = Files.readAllBytes(Paths.get(inputPath));
            LOGGER.info("Read input file: {} bytes", inputData.length);

            // ファイル全体を一括変換
            byte[] convertedData = CodeConverter.convertCharset(
                inputData,
                parameters.getSourceCharsetSingle(),
                parameters.getTargetCharsetSingle()
            );
            
            LOGGER.info("Converted: {} bytes -> {} bytes", inputData.length, convertedData.length);

            // ファイル全体を出力
            Files.write(Paths.get(outputPath), convertedData);
            LOGGER.info("Written output file: {} bytes", convertedData.length);

            LOGGER.info("Processing completed successfully");

        } catch (CharacterCodingException e) {
            LOGGER.error("Character coding error: {}", e.getMessage());
            throw e;
        } catch (IOException e) {
            LOGGER.error("File I/O error: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * レコード単位の処理は使用しない。
     * processFile() で全体一括変換を実施。
     * 
     * @param record 入力レコード
     * @return 変換後のレコード
     * @throws CharacterCodingException 文字コード変換エラー
     */
    @Override
    public byte[] processRecord(byte[] record) throws CharacterCodingException {
        throw new UnsupportedOperationException(
            "FileABProcessor does not support record-level processing. Use processFile() instead.");
    }
}
