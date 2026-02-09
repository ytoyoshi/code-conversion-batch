package com.example.batch.processor;

import com.example.batch.param.BatchParameter;
import com.example.batch.util.CodeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * 可変長ファイル変換処理クラス（FILE_E、FILE_F用）。
 * 
 * <p>
 * 可変長レコードファイルを変換する。
 * BDW、RDW、電文長を再計算し、制御文字の変換も行う。
 * </p>
 */
public class VariableLengthConverter {
    private static final Logger logger = LoggerFactory.getLogger(VariableLengthConverter.class);
    
    // フィールド位置定義
    private static final int BDW_SIZE = 4;           // ブロック長サイズ
    private static final int RDW_SIZE = 4;           // レコード長サイズ
    private static final int FIXED_PART_SIZE = 26;   // 固定部サイズ（8-33バイト）
    private static final int MESSAGE_LEN_SIZE = 2;   // 電文長サイズ
    private static final int TERMINATOR_SIZE = 1;    // 終端符号サイズ
    
    private static final int FIXED_PART_START = BDW_SIZE + RDW_SIZE;
    private static final int MESSAGE_LEN_START = FIXED_PART_START + FIXED_PART_SIZE;
    private static final int MESSAGE_START = MESSAGE_LEN_START + MESSAGE_LEN_SIZE;
    
    private final BatchParameter param;
    private final boolean isToEbcdic;
    
    /**
     * コンストラクタ。
     * 
     * @param param バッチパラメータ
     */
    public VariableLengthConverter(BatchParameter param) {
        this.param = param;
        // 変換先がCP930の場合、EBCDIC方向の変換
        this.isToEbcdic = param.getTargetCharsetSingle().toUpperCase().contains("930") ||
                          param.getTargetCharsetSingle().toUpperCase().contains("EBCDIC");
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
        logger.info("変換方向: {}", isToEbcdic ? "EBCDIC" : "JIS/UTF-8");
        
        int blockCount = 0;
        
        try (BufferedInputStream bis = new BufferedInputStream(
                new FileInputStream(param.getInputFilePath()));
             BufferedOutputStream bos = new BufferedOutputStream(
                new FileOutputStream(param.getOutputFilePath()))) {
            
            while (bis.available() > 0) {
                blockCount++;
                
                // ブロック処理
                byte[] convertedBlock = processBlock(bis, blockCount);
                
                if (convertedBlock != null && convertedBlock.length > 0) {
                    bos.write(convertedBlock);
                    logger.debug("ブロック {} 変換完了: {} bytes", blockCount, convertedBlock.length);
                }
            }
        }
        
        logger.info("ブロック処理完了: 総数={}", blockCount);
        logger.info("========== 可変長ファイル変換処理完了 ==========");
    }
    
    /**
     * 1ブロック分の処理を行う。
     * 
     * @param bis 入力ストリーム
     * @param blockNumber ブロック番号
     * @return 変換後ブロックデータ
     * @throws IOException 入出力エラー
     */
    private byte[] processBlock(BufferedInputStream bis, int blockNumber) throws IOException {
        // BDW（ブロック長）を読み込む
        byte[] bdwBytes = new byte[BDW_SIZE];
        int read = bis.read(bdwBytes);
        if (read != BDW_SIZE) {
            logger.warn("ブロック {} のBDW読み込みエラー: 期待={}, 実際={}", 
                blockNumber, BDW_SIZE, read);
            return new byte[0];
        }
        
        int blockLength = bytesToInt(bdwBytes);
        logger.debug("ブロック {} BDW={}", blockNumber, blockLength);
        
        // ブロックデータを読み込む（BDWを除く）
        int dataLength = blockLength - BDW_SIZE;
        byte[] blockData = new byte[dataLength];
        read = bis.read(blockData);
        if (read != dataLength) {
            logger.warn("ブロック {} データ読み込みエラー: 期待={}, 実際={}", 
                blockNumber, dataLength, read);
        }
        
        // レコード処理
        byte[] convertedData = processRecord(blockData);
        
        // 新しいBDWを計算
        int newBlockLength = BDW_SIZE + convertedData.length;
        byte[] newBdw = intToBytes(newBlockLength);
        
        // 変換後ブロックを構築
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(newBdw);
        baos.write(convertedData);
        
        return baos.toByteArray();
    }
    
    /**
     * レコードを処理する。
     * 
     * @param recordData レコードデータ（RDW含む）
     * @return 変換後レコードデータ
     * @throws IOException 入出力エラー
     */
    private byte[] processRecord(byte[] recordData) throws IOException {
        if (recordData.length < RDW_SIZE) {
            logger.warn("レコードデータが短すぎます: {} bytes", recordData.length);
            return recordData;
        }
        
        // RDW（レコード長）を取得
        byte[] rdwBytes = new byte[RDW_SIZE];
        System.arraycopy(recordData, 0, rdwBytes, 0, RDW_SIZE);
        int recordLength = bytesToInt(rdwBytes);
        
        logger.debug("RDW={}", recordLength);
        
        // 各フィールドを抽出
        byte[] fixedPart = extractField(recordData, RDW_SIZE, FIXED_PART_SIZE);
        byte[] messageLenBytes = extractField(recordData, MESSAGE_LEN_START - RDW_SIZE, MESSAGE_LEN_SIZE);
        
        int messageLength = bytesToInt(messageLenBytes);
        byte[] message = extractField(recordData, MESSAGE_START - RDW_SIZE, messageLength);
        byte[] terminator = extractField(recordData, 
            MESSAGE_START - RDW_SIZE + messageLength, TERMINATOR_SIZE);
        
        logger.debug("電文長={}, 終端符号=0x{}", 
            messageLength, String.format("%02X", terminator[0]));
        
        // 固定部を変換（英数のみ）
        byte[] convertedFixed = CodeConverter.convertCharset(
            fixedPart,
            param.getSourceCharsetSingle(),
            param.getTargetCharsetSingle()
        );
        
        // 電文を変換（英数半角カナ含む）
        byte[] convertedMessage = CodeConverter.convertCharset(
            message,
            param.getSourceCharsetSingle(),
            param.getTargetCharsetSingle()
        );
        
        // 制御文字（終端符号）を変換
        byte[] convertedTerminator = convertControlCharacter(
            terminator,
            isToEbcdic
        );
        
        // 新しい電文長を計算
        int newMessageLength = convertedMessage.length;
        byte[] newMessageLenBytes = intToBytes(newMessageLength);
        // 電文長は2バイトなので下位2バイトのみ使用
        byte[] newMessageLen = new byte[MESSAGE_LEN_SIZE];
        System.arraycopy(newMessageLenBytes, 2, newMessageLen, 0, MESSAGE_LEN_SIZE);
        
        // 新しいレコード長を計算
        int newRecordLength = RDW_SIZE + convertedFixed.length + MESSAGE_LEN_SIZE + 
                             convertedMessage.length + convertedTerminator.length;
        byte[] newRdw = intToBytes(newRecordLength);
        
        // 変換後レコードを構築
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(newRdw);
        baos.write(convertedFixed);
        baos.write(newMessageLen);
        baos.write(convertedMessage);
        baos.write(convertedTerminator);
        
        logger.debug("レコード変換: {} bytes -> {} bytes", recordLength, newRecordLength);
        
        return baos.toByteArray();
    }
    
    /**
     * フィールドを抽出する。
     * 
     * @param data ソースデータ
     * @param offset オフセット
     * @param length 長さ
     * @return 抽出されたフィールド
     */
    private byte[] extractField(byte[] data, int offset, int length) {
        if (offset + length > data.length) {
            logger.warn("フィールド抽出範囲エラー: offset={}, length={}, dataLength={}", 
                offset, length, data.length);
            length = Math.max(0, data.length - offset);
        }
        
        byte[] field = new byte[length];
        System.arraycopy(data, offset, field, 0, length);
        return field;
    }
    
    /**
     * バイト配列を整数に変換する（ビッグエンディアン）。
     * 
     * @param bytes バイト配列（4バイト）
     * @return 整数値
     */
    private int bytesToInt(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return buffer.getInt();
    }
    
    /**
     * 整数をバイト配列に変換する（ビッグエンディアン）。
     * 
     * @param value 整数値
     * @return バイト配列（4バイト）
     */
    private byte[] intToBytes(int value) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(value);
        return buffer.array();
    }
    
    /**
     * 制御文字を変換する（終端符号用）。
     * 
     * <p>
     * EBCDIC(0xB4) ⇔ JIS/UTF-8(0x74) の変換を行う。
     * </p>
     * 
     * @param data バイト列
     * @param isToEbcdic trueの場合EBCDIC方向、falseの場合JIS/UTF-8方向
     * @return 変換後バイト列
     */
    private byte[] convertControlCharacter(byte[] data, boolean isToEbcdic) {
        byte[] result = new byte[data.length];
        System.arraycopy(data, 0, result, 0, data.length);
        
        if (isToEbcdic) {
            // JIS/UTF-8 → EBCDIC: 0x74 → 0xB4
            for (int i = 0; i < result.length; i++) {
                if (result[i] == 0x74) {
                    result[i] = (byte) 0xB4;
                }
            }
        } else {
            // EBCDIC → JIS/UTF-8: 0xB4 → 0x74
            for (int i = 0; i < result.length; i++) {
                if (result[i] == (byte) 0xB4) {
                    result[i] = 0x74;
                }
            }
        }
        
        return result;
    }
}
