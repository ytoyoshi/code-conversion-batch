package com.example.batch.processor;

import com.example.batch.config.BatchParameters;
import com.example.batch.config.FileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;

/**
 * レコード処理の基底クラス。
 * ファイルの読み込み・書き込み処理と、共通的なフィールド抽出処理を提供。
 */
public abstract class RecordProcessor {
    
    protected static final Logger LOGGER = LoggerFactory.getLogger(RecordProcessor.class);
    
    protected final BatchParameters parameters;
    protected final FileType fileType;

    /**
     * コンストラクタ
     * 
     * @param parameters バッチパラメータ
     * @param fileType ファイルタイプ
     */
    public RecordProcessor(BatchParameters parameters, FileType fileType) {
        this.parameters = parameters;
        this.fileType = fileType;
    }

    /**
     * レコード変換処理(サブクラスで実装)
     * 
     * @param record 入力レコード
     * @return 変換後のレコード
     * @throws CharacterCodingException 文字コード変換エラー
     */
    public abstract byte[] processRecord(byte[] record) throws CharacterCodingException;

    /**
     * ファイル全体を処理
     * 
     * @param inputPath 入力ファイルパス
     * @param outputPath 出力ファイルパス
     * @throws IOException ファイルI/Oエラー
     * @throws CharacterCodingException 文字コード変換エラー
     */
    public void processFile(String inputPath, String outputPath) 
            throws IOException, CharacterCodingException {
        
        LOGGER.info("Processing file: {} -> {}", inputPath, outputPath);
        LOGGER.info("File type: {}", fileType);
        LOGGER.info("Source charset: single={}, double={}", 
            parameters.getSourceCharsetSingleName(), 
            parameters.getSourceCharsetDoubleName());
        LOGGER.info("Target charset: single={}, double={}", 
            parameters.getTargetCharsetSingleName(), 
            parameters.getTargetCharsetDoubleName());

        int recordCount = 0;
        int errorCount = 0;

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inputPath));
             BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputPath))) {
            
            byte[] record;
            while ((record = readRecord(bis)) != null) {
                recordCount++;
                
                try {
                    byte[] convertedRecord = processRecord(record);
                    bos.write(convertedRecord);
                    
                    if (recordCount % 1000 == 0) {
                        LOGGER.debug("Processed {} records", recordCount);
                    }
                    
                } catch (CharacterCodingException e) {
                    errorCount++;
                    LOGGER.error("Character coding error at record {}: {}", recordCount, e.getMessage());
                    throw new CharacterCodingException();
                }
            }
            
            bos.flush();
        }

        LOGGER.info("Processing completed: {} records processed, {} errors", recordCount, errorCount);
        
        if (errorCount > 0) {
            throw new CharacterCodingException();
        }
    }

    /**
     * レコードを読み込む(固定長 or 可変長)
     * 
     * @param bis 入力ストリーム
     * @return 読み込んだレコード。EOFの場合null
     * @throws IOException 読み込みエラー
     */
    protected byte[] readRecord(BufferedInputStream bis) throws IOException {
        if (fileType.isVariableLength()) {
            return readVariableLengthRecord(bis);
        } else {
            return readFixedLengthRecord(bis, fileType.getRecordLength());
        }
    }

    /**
     * 固定長レコードを読み込む
     */
    private byte[] readFixedLengthRecord(BufferedInputStream bis, int recordLength) throws IOException {
        byte[] record = new byte[recordLength];
        int bytesRead = bis.read(record);
        
        if (bytesRead == -1) {
            return null;  // EOF
        }
        
        if (bytesRead != recordLength) {
            throw new IOException("Incomplete record read: expected " + recordLength + 
                                 " bytes, got " + bytesRead + " bytes");
        }
        
        return record;
    }

    /**
     * 可変長レコードを読み込む
     * 先頭4バイト: ブロック長
     * 次の4バイト: レコード長
     * 残り: データ
     */
    private byte[] readVariableLengthRecord(BufferedInputStream bis) throws IOException {
        // ブロック長を読み込み(4バイト)
        byte[] blockLengthBytes = new byte[4];
        int bytesRead = bis.read(blockLengthBytes);
        
        if (bytesRead == -1) {
            return null;  // EOF
        }
        
        if (bytesRead != 4) {
            throw new IOException("Incomplete block length: expected 4 bytes, got " + bytesRead);
        }
        
        int blockLength = readInt32BigEndian(blockLengthBytes, 0);
        
        // 残りのブロックを読み込み(ブロック長 - 4バイト)
        byte[] remainingBlock = new byte[blockLength - 4];
        bytesRead = bis.read(remainingBlock);
        
        if (bytesRead != remainingBlock.length) {
            throw new IOException("Incomplete block read: expected " + remainingBlock.length + 
                                 " bytes, got " + bytesRead);
        }
        
        // ブロック長 + 残りを結合して返す
        byte[] fullRecord = new byte[blockLength];
        System.arraycopy(blockLengthBytes, 0, fullRecord, 0, 4);
        System.arraycopy(remainingBlock, 0, fullRecord, 4, remainingBlock.length);
        
        return fullRecord;
    }

    /**
     * 文字数ベースでデータを抽出(UTF-8用)
     * 
     * @param data 元データ
     * @param startCharPosition 開始文字位置(0始まり)
     * @param lengthInChars 文字数
     * @return 抽出したバイト列
     */
    protected byte[] extractByCharCount(byte[] data, int startCharPosition, int lengthInChars) {
        try {
            // UTF-8として文字列に変換
            String str = new String(data, parameters.getSourceCharsetSingle());
            
            // 文字位置で部分文字列を抽出
            int endCharPosition = Math.min(startCharPosition + lengthInChars, str.length());
            String extracted = str.substring(startCharPosition, endCharPosition);
            
            // バイト列に戻す(UTF-8)
            return extracted.getBytes(parameters.getSourceCharsetSingle());
            
        } catch (Exception e) {
            LOGGER.error("Error extracting by char count: start={}, length={}", 
                        startCharPosition, lengthInChars, e);
            throw new RuntimeException("Failed to extract by char count", e);
        }
    }

    /**
     * バイト位置ベースでデータを抽出(JIS/EBCDIC用)
     * 
     * @param data 元データ
     * @param startBytePosition 開始バイト位置(0始まり)
     * @param endBytePosition 終了バイト位置(0始まり、この位置を含む)
     * @return 抽出したバイト列
     */
    protected byte[] extractByBytePosition(byte[] data, int startBytePosition, int endBytePosition) {
        int length = endBytePosition - startBytePosition + 1;
        
        if (startBytePosition < 0 || endBytePosition >= data.length || length <= 0) {
            throw new IllegalArgumentException(
                "Invalid byte position: start=" + startBytePosition + 
                ", end=" + endBytePosition + ", data length=" + data.length);
        }
        
        byte[] extracted = new byte[length];
        System.arraycopy(data, startBytePosition, extracted, 0, length);
        
        return extracted;
    }

    /**
     * 入力ソースがUTF-8かどうかを判定
     * 
     * @return UTF-8の場合true
     */
    protected boolean isUtf8Source() {
        Charset charset = parameters.getSourceCharsetSingle();
        return charset.name().toUpperCase().contains("UTF-8") || 
               charset.name().toUpperCase().equals("UTF8");
    }

    /**
     * ビッグエンディアンで4バイト整数を読み込む
     * 
     * @param data データ
     * @param offset オフセット
     * @return 整数値
     */
    protected int readInt32BigEndian(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) |
               ((data[offset + 1] & 0xFF) << 16) |
               ((data[offset + 2] & 0xFF) << 8) |
               (data[offset + 3] & 0xFF);
    }

    /**
     * ビッグエンディアンで4バイト整数を書き込む
     * 
     * @param value 整数値
     * @return 4バイト配列
     */
    protected byte[] writeInt32BigEndian(int value) {
        byte[] bytes = new byte[4];
        bytes[0] = (byte) ((value >> 24) & 0xFF);
        bytes[1] = (byte) ((value >> 16) & 0xFF);
        bytes[2] = (byte) ((value >> 8) & 0xFF);
        bytes[3] = (byte) (value & 0xFF);
        return bytes;
    }
}
