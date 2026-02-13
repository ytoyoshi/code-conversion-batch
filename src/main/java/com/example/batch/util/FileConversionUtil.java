package com.example.batch.util;

import com.example.batch.model.KanjiFieldDefinition;
import com.example.batch.model.MixedFileConversionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * ファイル変換ユーティリティクラス。
 * 
 * <p>
 * ファイルタイプ別の変換ロジックを提供する。
 * 入出力処理は含まず、バイト配列の変換のみを行う。
 * </p>
 */
public class FileConversionUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileConversionUtil.class);
    
    // ========== 全体変換用定数 ==========
    private static final int WHOLE_FILE_RECORD_LENGTH = 210;
    private static final int CODE_TYPE_POSITION = 5;
    
    // ========== 混合ファイル変換用定数 ==========
    private static final int MIXED_FILE_RECORD_LENGTH = 380;
    private static final int SIMPLE_RECORD_CHAR_LENGTH = 380;
    private static final int DATA_RECORD_CHAR_LENGTH_TYPE1 = 350;
    private static final int DATA_RECORD_CHAR_LENGTH_TYPE2 = 300;
    private static final byte HEADER_RECORD_TYPE = '1';
    private static final byte DATA_RECORD_TYPE = '2';
    private static final byte TRAILER_RECORD_TYPE = '8';
    private static final byte END_RECORD_TYPE = '9';
    private static final byte DATA_TYPE_1 = '1';
    private static final byte DATA_TYPE_2 = '2';
    
    // 漢字項目定義（文字数ベース：UTF-8用）
    private static final Map<Byte, KanjiFieldDefinition[]> KANJI_FIELD_DEFS_CHARACTER = new HashMap<>();
    
    // 漢字項目定義（バイト数ベース：JIS/EBCDIC用）
    private static final Map<Byte, KanjiFieldDefinition[]> KANJI_FIELD_DEFS_BYTE = new HashMap<>();
    
    static {
        // 文字数ベース定義（UTF-8用）
        KANJI_FIELD_DEFS_CHARACTER.put(DATA_TYPE_1, new KanjiFieldDefinition[]{
            new KanjiFieldDefinition(50, 99),
            new KanjiFieldDefinition(150, 199)
        });
        KANJI_FIELD_DEFS_CHARACTER.put(DATA_TYPE_2, new KanjiFieldDefinition[]{
            new KanjiFieldDefinition(80, 129),
            new KanjiFieldDefinition(200, 249)
        });
        
        // バイト数ベース定義（JIS/EBCDIC用）
        KANJI_FIELD_DEFS_BYTE.put(DATA_TYPE_1, new KanjiFieldDefinition[]{
            new KanjiFieldDefinition(50, 99),
            new KanjiFieldDefinition(150, 199)
        });
        KANJI_FIELD_DEFS_BYTE.put(DATA_TYPE_2, new KanjiFieldDefinition[]{
            new KanjiFieldDefinition(80, 129),
            new KanjiFieldDefinition(200, 249)
        });
    }
    
    // ========== 可変長ファイル変換用定数 ==========
    private static final int BDW_SIZE = 4;
    private static final int RDW_SIZE = 4;
    private static final int FIXED_PART_SIZE = 26;
    private static final int MESSAGE_LEN_START = 30;
    private static final int MESSAGE_LEN_SIZE = 2;
    private static final int MESSAGE_START = 32;
    private static final int TERMINATOR_SIZE = 1;
    
    /**
     * プライベートコンストラクタ（インスタンス化禁止）。
     */
    private FileConversionUtil() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    // ========== 公開メソッド ==========
    
    /**
     * 全体変換（FILE_A、FILE_B用）。
     * 
     * @param inputBytes 入力バイト配列
     * @param sourceCharset 変換元文字コード
     * @param targetCharset 変換先文字コード
     * @return 変換後バイト配列
     */
    public static byte[] convertWholeFile(
            byte[] inputBytes,
            String sourceCharset,
            String targetCharset) {
        
        // 全体を一括変換
        byte[] converted = CodeConverter.convertCharset(
            inputBytes,
            sourceCharset,
            targetCharset
        );
        
        // ヘッダーレコードのコード区分を変換
        updateHeaderCodeType(converted, WHOLE_FILE_RECORD_LENGTH, targetCharset);
        
        return converted;
    }
    
    /**
     * 混合ファイル変換（FILE_C、FILE_D用）。
     * 
     * @param inputBytes 入力バイト配列
     * @param config 変換設定
     * @return 変換後バイト配列
     */
    public static byte[] convertMixedFile(
            byte[] inputBytes,
            MixedFileConversionConfig config) {
        
        // UTF-8判定
        boolean isUtf8 = isUtf8(config.getSourceCharsetSingle());
        
        if (isUtf8) {
            return convertMixedFileCharacterBased(inputBytes, config);
        } else {
            return convertMixedFileByteBased(inputBytes, config);
        }
    }
    
    /**
     * 可変長ファイル変換（FILE_E、FILE_F用）。
     * 
     * @param inputBytes 入力バイト配列
     * @param sourceCharsetSingle 変換元1バイト文字コード
     * @param targetCharsetSingle 変換先1バイト文字コード
     * @return 変換後バイト配列
     */
    public static byte[] convertVariableLengthFile(
            byte[] inputBytes,
            String sourceCharsetSingle,
            String targetCharsetSingle) {
        
        // EBCDIC変換方向判定
        boolean isToEbcdic = isTargetEbcdic(targetCharsetSingle);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteArrayInputStream bais = new ByteArrayInputStream(inputBytes);
        
        try {
            int blockNumber = 0;
            byte[] bdw = new byte[BDW_SIZE];
            
            // BDWを読み込んでブロック単位で処理
            while (bais.read(bdw) == BDW_SIZE) {
                blockNumber++;
                int blockLength = bytesToInt(bdw);
                
                // ブロック全体を読み込む（BDW含む）
                byte[] blockData = new byte[blockLength];
                System.arraycopy(bdw, 0, blockData, 0, BDW_SIZE);
                int read = bais.read(blockData, BDW_SIZE, blockLength - BDW_SIZE);
                
                if (read != blockLength - BDW_SIZE) {
                    logger.warn("ブロック読み込み不完全: block={}, 期待={}, 実際={}",
                        blockNumber, blockLength - BDW_SIZE, read);
                }
                
                // ブロックを処理
                byte[] convertedBlock = processBlock(blockData, sourceCharsetSingle,
                    targetCharsetSingle, isToEbcdic);
                baos.write(convertedBlock);
            }
            
            return baos.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("可変長ファイル変換エラー", e);
        }
    }
    
    // ========== 内部メソッド（全体変換用） ==========
    
    /**
     * ヘッダーレコードのコード区分を変換する。
     * 
     * @param data 変換後のバイト配列
     * @param recordLength レコード長
     * @param targetCharset 変換先文字コード
     */
    private static void updateHeaderCodeType(byte[] data, int recordLength, String targetCharset) {
        byte targetCodeType = isTargetEbcdic(targetCharset) ? (byte)'1' : (byte)'0';

        for (int offset = 0; offset < data.length; offset += recordLength) {
            if (offset + recordLength > data.length) {
                break;
            }

            // ヘッダーレコード（1バイト目が'1'）の場合、コード区分を更新
            if (data[offset] == (byte)'1') {
                data[offset + CODE_TYPE_POSITION] = targetCodeType;
            }
        }
    }

    // ========== 内部メソッド（混合ファイル変換用） ==========
    
    private static byte[] convertMixedFileCharacterBased(
            byte[] inputBytes,
            MixedFileConversionConfig config) {
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Charset sourceCharset = Charset.forName(config.getSourceCharsetSingle());
        
        try (Reader reader = new InputStreamReader(
                new ByteArrayInputStream(inputBytes), sourceCharset)) {
            
            int ch;
            while ((ch = reader.read()) != -1) {
                char recordType = (char) ch;
                
                String record;
                byte[] converted;
                
                if (recordType == DATA_RECORD_TYPE) {
                    // データレコード: データ種別（2文字目）をさらに先読み
                    int ch2 = reader.read();
                    if (ch2 == -1) {
                        break;
                    }
                    char dataType = (char) ch2;
                    
                    // データ種別に応じた残り文字数を決定（先読みした2文字分を引く）
                    int remainingChars = getDataRecordRemainingChars(dataType);
                    
                    char[] buf = new char[remainingChars];
                    int charsRead = readFully(reader, buf, remainingChars);
                    
                    // 先読みした2文字（レコード区分+データ種別）と残りを結合
                    record = String.valueOf(recordType) + dataType + new String(buf, 0, charsRead);
                    converted = convertDataRecordCharacterBased(record, config);
                    
                } else {
                    // ヘッダー/トレーラー/エンド: 残り379文字を読む
                    int remainingChars = SIMPLE_RECORD_CHAR_LENGTH - 1;
                    char[] buf = new char[remainingChars];
                    int charsRead = readFully(reader, buf, remainingChars);
                    
                    record = String.valueOf(recordType) + new String(buf, 0, charsRead);
                    converted = convertSimpleRecord(
                        record.getBytes(sourceCharset),
                        config.getSourceCharsetSingle(),
                        config.getTargetCharsetSingle()
                    );
                }
                
                outputStream.write(converted);
            }
            
            return outputStream.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("混合ファイル変換エラー（文字数ベース）", e);
        }
    }
    
    private static byte[] convertMixedFileByteBased(
            byte[] inputBytes,
            MixedFileConversionConfig config) {
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try (ByteArrayInputStream bis = new ByteArrayInputStream(inputBytes)) {
            
            byte[] record = new byte[MIXED_FILE_RECORD_LENGTH];
            int bytesRead;
            
            while ((bytesRead = bis.read(record)) != -1) {
                if (bytesRead < MIXED_FILE_RECORD_LENGTH) {
                    logger.warn("レコード長不一致（バイト数）: 期待={}, 実際={}",
                        MIXED_FILE_RECORD_LENGTH, bytesRead);
                }
                
                // レコード区分を判定
                byte recordType = record[0];
                
                byte[] converted;
                if (recordType == HEADER_RECORD_TYPE || 
                    recordType == TRAILER_RECORD_TYPE || 
                    recordType == END_RECORD_TYPE) {
                    // ヘッダー/トレーラー/エンド: 1バイト文字のみ全体変換
                    byte[] target = new byte[bytesRead];
                    System.arraycopy(record, 0, target, 0, bytesRead);
                    converted = convertSimpleRecord(
                        target,
                        config.getSourceCharsetSingle(),
                        config.getTargetCharsetSingle()
                    );
                    
                } else if (recordType == DATA_RECORD_TYPE) {
                    // データレコード: 選択的に漢字変換（バイト数ベース）
                    converted = convertDataRecord(record, bytesRead, config);
                    
                } else {
                    // 不明なレコード区分: そのまま出力
                    converted = new byte[bytesRead];
                    System.arraycopy(record, 0, converted, 0, bytesRead);
                }
                
                outputStream.write(converted);
            }
            
            return outputStream.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("混合ファイル変換エラー（バイト数ベース）", e);
        }
    }
    
    private static byte[] convertSimpleRecord(
            byte[] data,
            String sourceCharset,
            String targetCharset) {
        
        // 文字コード変換
        byte[] converted = CodeConverter.convertCharset(data, sourceCharset, targetCharset);
        
        // ヘッダーレコード（1バイト目が'1'）の場合、コード区分を更新
        if (converted.length > 0 && converted[0] == '1') {
            if (converted.length > CODE_TYPE_POSITION) {
                converted[CODE_TYPE_POSITION] = isTargetEbcdic(targetCharset) ? (byte)'1' : (byte)'0';
            }
        }
        
        return converted;
    }
    
    private static byte[] convertDataRecordCharacterBased(
            String line,
            MixedFileConversionConfig config) {
        
        // データ種別を取得（2文字目、0-based indexでは1）
        char dataType = line.charAt(1);
        
        // 作業用バッファ
        ByteArrayOutputStream baos = new ByteArrayOutputStream(line.length() * 3);
        
        try {
            // データ種別に応じた漢字項目定義を取得（文字数ベース）
            KanjiFieldDefinition[] defs = KANJI_FIELD_DEFS_CHARACTER.get((byte) dataType);
            if (defs == null) {
                // 定義がない場合は全体を1バイト文字として変換
                return convertSimpleRecord(
                    line.getBytes(Charset.forName(config.getSourceCharsetSingle())),
                    config.getSourceCharsetSingle(),
                    config.getTargetCharsetSingle()
                );
            }
            
            int currentPos = 0;
            
            for (KanjiFieldDefinition def : defs) {
                // 漢字項目の前の1バイト文字部分を変換
                if (currentPos < def.getStartPos()) {
                    String singleByteField = line.substring(currentPos, Math.min(def.getStartPos(), line.length()));
                    byte[] converted = CodeConverter.convertCharset(
                        singleByteField.getBytes(Charset.forName(config.getSourceCharsetSingle())),
                        config.getSourceCharsetSingle(),
                        config.getTargetCharsetSingle()
                    );
                    baos.write(converted);
                }
                
                // 漢字項目を変換
                int endPos = Math.min(def.getEndPos() + 1, line.length());
                if (def.getStartPos() < line.length()) {
                    String kanjiField = line.substring(def.getStartPos(), endPos);
                    byte[] kanjiBytes = kanjiField.getBytes(Charset.forName(config.getSourceCharsetDouble()));
                    byte[] convertedKanji;
                    if (config.isConvertDoubleByteCharset()) {
                        // 変換元と変換先が異なるため漢字変換を実施
                        convertedKanji = CodeConverter.convertKanjiCharset(
                            kanjiBytes,
                            config.getSourceCharsetDouble(),
                            config.getTargetCharsetDouble()
                        );
                    } else {
                        // 変換元と変換先が同一のためそのまま出力
                        convertedKanji = kanjiBytes;
                    }
                    baos.write(convertedKanji);
                }
                
                currentPos = def.getEndPos() + 1;
            }
            
            // 最後の漢字項目以降の1バイト文字部分を変換
            if (currentPos < line.length()) {
                String singleByteField = line.substring(currentPos);
                byte[] converted = CodeConverter.convertCharset(
                    singleByteField.getBytes(Charset.forName(config.getSourceCharsetSingle())),
                    config.getSourceCharsetSingle(),
                    config.getTargetCharsetSingle()
                );
                baos.write(converted);
            }
            
            return baos.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("データレコード変換エラー（文字数ベース）", e);
        }
    }
    
    private static byte[] convertDataRecord(
            byte[] record,
            int length,
            MixedFileConversionConfig config) {
        
        // データ種別を取得（2バイト目）
        byte dataType = record[1];
        
        // 作業用バッファ（最大長を確保）
        ByteArrayOutputStream baos = new ByteArrayOutputStream(length * 2);
        
        try {
            // データ種別に応じた漢字項目定義を取得（バイト数ベース）
            KanjiFieldDefinition[] defs = KANJI_FIELD_DEFS_BYTE.get(dataType);
            if (defs == null) {
                // 定義がない場合は全体を1バイト文字として変換
                byte[] target = new byte[length];
                System.arraycopy(record, 0, target, 0, length);
                return CodeConverter.convertCharset(
                    target,
                    config.getSourceCharsetSingle(),
                    config.getTargetCharsetSingle()
                );
            }
            
            int currentPos = 0;
            
            for (KanjiFieldDefinition def : defs) {
                // 漢字項目の前の1バイト文字部分を変換
                if (currentPos < def.getStartPos()) {
                    byte[] singleByteField = extractBytes(record, currentPos, def.getStartPos() - 1);
                    byte[] converted = CodeConverter.convertCharset(
                        singleByteField,
                        config.getSourceCharsetSingle(),
                        config.getTargetCharsetSingle()
                    );
                    baos.write(converted);
                }
                
                // 漢字項目を変換
                byte[] kanjiField = extractBytes(record, def.getStartPos(), def.getEndPos());
                byte[] convertedKanji;
                if (config.isConvertDoubleByteCharset()) {
                    // 変換元と変換先が異なるため漢字変換を実施
                    convertedKanji = CodeConverter.convertKanjiCharset(
                        kanjiField,
                        config.getSourceCharsetDouble(),
                        config.getTargetCharsetDouble()
                    );
                } else {
                    // 変換元と変換先が同一のためそのまま出力
                    convertedKanji = kanjiField;
                }
                baos.write(convertedKanji);
                
                currentPos = def.getEndPos() + 1;
            }
            
            // 最後の漢字項目以降の1バイト文字部分を変換
            if (currentPos < length) {
                byte[] singleByteField = extractBytes(record, currentPos, length - 1);
                byte[] converted = CodeConverter.convertCharset(
                    singleByteField,
                    config.getSourceCharsetSingle(),
                    config.getTargetCharsetSingle()
                );
                baos.write(converted);
            }
            
            return baos.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("データレコード変換エラー", e);
        }
    }
    
    private static int getDataRecordRemainingChars(char dataType) {
        if (dataType == DATA_TYPE_1) {
            return DATA_RECORD_CHAR_LENGTH_TYPE1 - 2;
        } else if (dataType == DATA_TYPE_2) {
            return DATA_RECORD_CHAR_LENGTH_TYPE2 - 2;
        } else {
            return DATA_RECORD_CHAR_LENGTH_TYPE1 - 2;
        }
    }
    
    private static int readFully(Reader reader, char[] buf, int len) throws IOException {
        int totalRead = 0;
        while (totalRead < len) {
            int read = reader.read(buf, totalRead, len - totalRead);
            if (read == -1) break;
            totalRead += read;
        }
        return totalRead;
    }
    
    private static byte[] extractBytes(byte[] source, int startPos, int endPos) {
        if (startPos < 0 || endPos >= source.length || startPos > endPos) {
            return new byte[0];
        }
        int length = endPos - startPos + 1;
        byte[] result = new byte[length];
        System.arraycopy(source, startPos, result, 0, length);
        return result;
    }
    // ========== 内部メソッド（可変長ファイル変換用） ==========
    
    private static byte[] processBlock(
            byte[] blockData,
            String sourceCharsetSingle,
            String targetCharsetSingle,
            boolean isToEbcdic) throws IOException {
        
        if (blockData.length < BDW_SIZE) {
            return new byte[0];
        }
        
        // BDWを取得
        byte[] bdwBytes = new byte[BDW_SIZE];
        System.arraycopy(blockData, 0, bdwBytes, 0, BDW_SIZE);
        int blockLength = bytesToInt(bdwBytes);
        
        // ブロックデータ（BDWを除く）を処理
        int dataLength = blockLength - BDW_SIZE;
        byte[] data = new byte[dataLength];
        System.arraycopy(blockData, BDW_SIZE, data, 0, dataLength);
        
        // レコード処理
        byte[] convertedData = processRecord(data, sourceCharsetSingle, targetCharsetSingle, isToEbcdic);
        
        // 新しいBDWを計算
        int newBlockLength = BDW_SIZE + convertedData.length;
        byte[] newBdw = intToBytes(newBlockLength);
        
        // 変換後ブロックを構築
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(newBdw);
        baos.write(convertedData);
        
        return baos.toByteArray();
    }
    
    private static byte[] processRecord(
            byte[] recordData,
            String sourceCharsetSingle,
            String targetCharsetSingle,
            boolean isToEbcdic) throws IOException {
        
        if (recordData.length < RDW_SIZE) {
            return recordData;
        }
        
        // RDW（レコード長）を取得
        byte[] rdwBytes = new byte[RDW_SIZE];
        System.arraycopy(recordData, 0, rdwBytes, 0, RDW_SIZE);
        
        // 各フィールドを抽出
        byte[] fixedPart = extractField(recordData, RDW_SIZE, FIXED_PART_SIZE);
        byte[] messageLenBytes = extractField(recordData, MESSAGE_LEN_START, MESSAGE_LEN_SIZE);

        int messageLength = bytesToInt(messageLenBytes);
        byte[] message = extractField(recordData, MESSAGE_START, messageLength);
        byte[] terminator = extractField(recordData,
            MESSAGE_START + messageLength, TERMINATOR_SIZE);
        
        // 固定部を変換（英数のみ）
        byte[] convertedFixed = CodeConverter.convertCharset(
            fixedPart,
            sourceCharsetSingle,
            targetCharsetSingle
        );
        
        // 電文を変換（英数半角カナ含む）
        byte[] convertedMessage = CodeConverter.convertCharset(
            message,
            sourceCharsetSingle,
            targetCharsetSingle
        );
        
        // 制御文字（終端符号）を変換
        byte[] convertedTerminator = convertControlCharacter(terminator, isToEbcdic);
        
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
        
        return baos.toByteArray();
    }
    
    private static byte[] extractField(byte[] data, int offset, int length) {
        if (offset + length > data.length) {
            length = Math.max(0, data.length - offset);
        }
        
        byte[] field = new byte[length];
        System.arraycopy(data, offset, field, 0, length);
        return field;
    }
    
    private static byte[] convertControlCharacter(byte[] data, boolean isToEbcdic) {
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

    // ========== ユーティリティメソッド ==========

    /**
     * UTF-8文字コードかどうかを判定する。
     *
     * @param charset 文字コード名
     * @return UTF-8の場合true
     */
    private static boolean isUtf8(String charset) {
        if (charset == null) {
            return false;
        }
        String normalized = charset.toUpperCase().replaceAll("-", "");
        return normalized.equals("UTF8");
    }

    /**
     * EBCDIC文字コードかどうかを判定する。
     *
     * @param charset 文字コード名
     * @return EBCDICの場合true
     */
    private static boolean isTargetEbcdic(String charset) {
        if (charset == null) {
            return false;
        }
        String normalized = charset.toUpperCase();
        return normalized.contains("CP930") || normalized.contains("IBM930");
    }

    /**
     * バイト配列をintに変換する（ビッグエンディアン）。
     *
     * @param bytes バイト配列（2バイトまたは4バイト）
     * @return int値
     */
    private static int bytesToInt(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return 0;
        }

        int result = 0;
        for (int i = 0; i < bytes.length && i < 4; i++) {
            result = (result << 8) | (bytes[i] & 0xFF);
        }
        return result;
    }

    /**
     * intをバイト配列に変換する（ビッグエンディアン、4バイト）。
     *
     * @param value int値
     * @return 4バイトのバイト配列
     */
    private static byte[] intToBytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }
}
