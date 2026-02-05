package com.example.batch.processor;

import com.example.batch.config.BatchParameters;
import com.example.batch.config.FileType;
import com.example.batch.util.CodeConverter;

import java.nio.charset.CharacterCodingException;

/**
 * FILE_A, FILE_B用のレコードプロセッサ。
 * 1バイト文字のみで構成される210バイト固定長ファイルを全体変換。
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
     * レコード全体を変換。
     * 1バイト文字のみなので、全体を一括で変換。
     * 
     * @param record 入力レコード(210バイト)
     * @return 変換後のレコード
     * @throws CharacterCodingException 文字コード変換エラー
     */
    @Override
    public byte[] processRecord(byte[] record) throws CharacterCodingException {
        
        if (record.length != fileType.getRecordLength()) {
            throw new IllegalArgumentException(
                "Invalid record length for " + fileType + ": expected " + 
                fileType.getRecordLength() + " bytes, got " + record.length + " bytes");
        }

        LOGGER.debug("Processing {} record: {} bytes", fileType, record.length);

        // 1バイト文字として全体を変換
        byte[] convertedRecord = CodeConverter.convertCharset(
            record,
            parameters.getSourceCharsetSingle(),
            parameters.getTargetCharsetSingle()
        );

        LOGGER.debug("Converted record: {} bytes -> {} bytes", 
                    record.length, convertedRecord.length);

        return convertedRecord;
    }
}
