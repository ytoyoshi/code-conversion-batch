package com.example.batch.processor;

import com.example.batch.config.BatchParameters;
import com.example.batch.config.FileType;
import com.example.batch.util.CodeConverter;

import java.io.ByteArrayOutputStream;
import java.nio.charset.CharacterCodingException;

/**
 * FILE_E, FILE_F用のレコードプロセッサ。
 * 可変長レコードを処理し、制御文字変換も実施。
 */
public class FileEFProcessor extends RecordProcessor {

    /**
     * コンストラクタ
     * 
     * @param parameters バッチパラメータ
     * @param fileType ファイルタイプ(FILE_E または FILE_F)
     */
    public FileEFProcessor(BatchParameters parameters, FileType fileType) {
        super(parameters, fileType);
        
        if (fileType != FileType.FILE_E && fileType != FileType.FILE_F) {
            throw new IllegalArgumentException(
                "FileEFProcessor only supports FILE_E and FILE_F, but got: " + fileType);
        }
    }

    /**
     * 可変長レコードを変換。
     * 
     * レコード構造:
     * - 0-3バイト: ブロック長(4バイト、ビッグエンディアン)
     * - 4-7バイト: レコード長(4バイト、ビッグエンディアン)
     * - 8バイト以降: 変換対象データ
     * 
     * 制御文字変換:
     * - EBCDIC(CP930): 0xB4 (u)
     * - JIS/UTF-8: 0x74 (t)
     * 
     * @param record 入力レコード
     * @return 変換後のレコード
     * @throws CharacterCodingException 文字コード変換エラー
     */
    @Override
    public byte[] processRecord(byte[] record) throws CharacterCodingException {
        
        if (record.length < 8) {
            throw new IllegalArgumentException(
                "Variable length record must be at least 8 bytes (block length + record length), " +
                "but got " + record.length + " bytes");
        }

        LOGGER.debug("Processing {} variable length record: {} bytes", fileType, record.length);

        try {
            // ブロック長とレコード長を読み込み
            int blockLength = readInt32BigEndian(record, 0);
            int recordLength = readInt32BigEndian(record, 4);
            
            LOGGER.debug("Original - Block length: {}, Record length: {}", blockLength, recordLength);

            // データ部分を抽出(8バイト目以降)
            byte[] dataBytes = new byte[record.length - 8];
            System.arraycopy(record, 8, dataBytes, 0, dataBytes.length);

            // データ部分を変換(制御文字変換も含む)
            byte[] convertedData = CodeConverter.convertCharset(
                dataBytes,
                parameters.getSourceCharsetSingle(),
                parameters.getTargetCharsetSingle()
            );

            // 新しいブロック長とレコード長を計算
            int newBlockLength = 4 + 4 + convertedData.length;  // ブロック長ヘッダー + レコード長ヘッダー + データ
            int newRecordLength = 4 + convertedData.length;     // レコード長ヘッダー + データ

            LOGGER.debug("Converted - Block length: {}, Record length: {}, Data: {} bytes",
                        newBlockLength, newRecordLength, convertedData.length);

            // 結果を組み立て
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            result.write(writeInt32BigEndian(newBlockLength));
            result.write(writeInt32BigEndian(newRecordLength));
            result.write(convertedData);

            return result.toByteArray();

        } catch (Exception e) {
            LOGGER.error("Error processing variable length record", e);
            throw new CharacterCodingException();
        }
    }
}
