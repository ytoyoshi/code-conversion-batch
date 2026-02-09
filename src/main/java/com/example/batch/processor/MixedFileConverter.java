package com.example.batch.processor;

import com.example.batch.param.BatchParameter;
import com.example.batch.util.CodeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * 混合ファイル変換処理クラス（FILE_C、FILE_D用）。
 * 
 * <p>
 * 1バイト文字と2バイト文字が混合するファイルを変換する。
 * データレコードの特定フィールドのみ漢字変換を実施する。
 * UTF-8の場合は文字数ベース、それ以外はバイト数ベースで処理する。
 * </p>
 */
public class MixedFileConverter {
    private static final Logger logger = LoggerFactory.getLogger(MixedFileConverter.class);
    
    private static final int RECORD_LENGTH = 380;
    private static final byte HEADER_RECORD_TYPE = '1';
    private static final byte DATA_RECORD_TYPE = '2';
    private static final byte TRAILER_RECORD_TYPE = '8';
    private static final byte END_RECORD_TYPE = '9';
    
    // データ種別定数
    private static final byte DATA_TYPE_1 = '1';
    private static final byte DATA_TYPE_2 = '2';
    
    // 漢字項目定義（文字数ベース）
    // UTF-8の場合は文字位置、それ以外はバイト位置として使用
    // 実際の業務要件に合わせて調整すること
    private static final Map<Byte, KanjiFieldDefinition[]> KANJI_FIELD_DEFS = new HashMap<>();
    
    static {
        // データ種別=1の場合の漢字項目位置
        KANJI_FIELD_DEFS.put(DATA_TYPE_1, new KanjiFieldDefinition[]{
            new KanjiFieldDefinition(50, 99),   // 位置1: 50-99（文字またはバイト）
            new KanjiFieldDefinition(150, 199)  // 位置2: 150-199（文字またはバイト）
        });
        
        // データ種別=2の場合の漢字項目位置
        KANJI_FIELD_DEFS.put(DATA_TYPE_2, new KanjiFieldDefinition[]{
            new KanjiFieldDefinition(80, 129),  // 位置1: 80-129（文字またはバイト）
            new KanjiFieldDefinition(200, 249)  // 位置2: 200-249（文字またはバイト）
        });
    }
    
    private final BatchParameter param;
    private final boolean isUtf8Source;
    
    /**
     * コンストラクタ。
     * 
     * @param param バッチパラメータ
     */
    public MixedFileConverter(BatchParameter param) {
        this.param = param;
        // 変換元がUTF-8かどうかを判定
        this.isUtf8Source = isUtf8(param.getSourceCharsetSingle());
    }
    
    /**
     * UTF-8判定。
     * 
     * @param charset 文字コード名
     * @return UTF-8の場合true
     */
    private boolean isUtf8(String charset) {
        return charset != null && 
               charset.toUpperCase().replace("-", "").replace("_", "").equals("UTF8");
    }
    
    /**
     * 混合ファイル変換処理を実行する。
     * 
     * @throws IOException ファイル入出力エラー
     */
    public void execute() throws IOException {
        logger.info("========== 混合ファイル変換処理開始 ==========");
        logger.info("ファイルタイプ: {}", param.getFileId());
        logger.info("入力ファイル: {}", param.getInputFilePath());
        logger.info("出力ファイル: {}", param.getOutputFilePath());
        logger.info("変換元1バイト文字コード: {}", param.getSourceCharsetSingle());
        logger.info("変換元2バイト文字コード: {}", param.getSourceCharsetDouble());
        logger.info("変換先1バイト文字コード: {}", param.getTargetCharsetSingle());
        logger.info("変換先2バイト文字コード: {}", param.getTargetCharsetDouble());
        logger.info("処理モード: {}", isUtf8Source ? "UTF-8文字数ベース" : "バイト数ベース");
        
        if (isUtf8Source) {
            // UTF-8の場合は文字数ベースで処理
            executeCharacterBased();
        } else {
            // それ以外はバイト数ベースで処理
            executeByteBased();
        }
        
        logger.info("========== 混合ファイル変換処理完了 ==========");
    }
    
    /**
     * 文字数ベースの変換処理（UTF-8用）。
     * 
     * @throws IOException ファイル入出力エラー
     */
    private void executeCharacterBased() throws IOException {
        int recordCount = 0;
        int headerCount = 0;
        int dataCount = 0;
        int trailerCount = 0;
        int endCount = 0;
        
        Charset sourceCharset = Charset.forName(param.getSourceCharsetSingle());
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    new FileInputStream(param.getInputFilePath()), sourceCharset));
             BufferedOutputStream bos = new BufferedOutputStream(
                new FileOutputStream(param.getOutputFilePath()))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                recordCount++;
                
                // レコード長チェック
                if (line.length() != RECORD_LENGTH) {
                    logger.warn("レコード長不一致（文字数）: 期待={}, 実際={}, レコード番号={}", 
                        RECORD_LENGTH, line.length(), recordCount);
                }
                
                // レコード区分を判定
                char recordType = line.charAt(0);
                
                byte[] converted;
                if (recordType == HEADER_RECORD_TYPE) {
                    // ヘッダーレコード: 1バイト文字のみ全体変換
                    converted = convertSimpleRecord(line);
                    headerCount++;
                    
                } else if (recordType == DATA_RECORD_TYPE) {
                    // データレコード: 選択的に漢字変換（文字数ベース）
                    converted = convertDataRecordCharacterBased(line);
                    dataCount++;
                    
                } else if (recordType == TRAILER_RECORD_TYPE) {
                    // トレーラーレコード: 1バイト文字のみ全体変換
                    converted = convertSimpleRecord(line);
                    trailerCount++;
                    
                } else if (recordType == END_RECORD_TYPE) {
                    // エンドレコード: 1バイト文字のみ全体変換
                    converted = convertSimpleRecord(line);
                    endCount++;
                    
                } else {
                    // 不明なレコード区分: そのまま出力
                    logger.warn("不明なレコード区分: {} (レコード番号: {})", recordType, recordCount);
                    converted = line.getBytes(sourceCharset);
                }
                
                bos.write(converted);
                // 改行コードは含めない（固定長のため）
            }
        }
        
        logger.info("レコード処理完了: 総数={}, ヘッダー={}, データ={}, トレーラー={}, エンド={}", 
            recordCount, headerCount, dataCount, trailerCount, endCount);
    }
    
    /**
     * バイト数ベースの変換処理（UTF-8以外用）。
     * 
     * @throws IOException ファイル入出力エラー
     */
    private void executeByteBased() throws IOException {
        int recordCount = 0;
        int headerCount = 0;
        int dataCount = 0;
        int trailerCount = 0;
        int endCount = 0;
        
        try (BufferedInputStream bis = new BufferedInputStream(
                new FileInputStream(param.getInputFilePath()));
             BufferedOutputStream bos = new BufferedOutputStream(
                new FileOutputStream(param.getOutputFilePath()))) {
            
            byte[] record = new byte[RECORD_LENGTH];
            int bytesRead;
            
            while ((bytesRead = bis.read(record)) != -1) {
                if (bytesRead != RECORD_LENGTH) {
                    logger.warn("レコード長不一致: 期待={}, 実際={}", RECORD_LENGTH, bytesRead);
                }
                
                recordCount++;
                
                // レコード区分を判定
                byte recordType = record[0];
                
                byte[] converted;
                if (recordType == HEADER_RECORD_TYPE) {
                    // ヘッダーレコード: 1バイト文字のみ全体変換
                    converted = convertHeaderRecord(record, bytesRead);
                    headerCount++;
                    
                } else if (recordType == DATA_RECORD_TYPE) {
                    // データレコード: 選択的に漢字変換（バイト数ベース）
                    converted = convertDataRecord(record, bytesRead);
                    dataCount++;
                    
                } else if (recordType == TRAILER_RECORD_TYPE) {
                    // トレーラーレコード: 1バイト文字のみ全体変換
                    converted = convertHeaderRecord(record, bytesRead);
                    trailerCount++;
                    
                } else if (recordType == END_RECORD_TYPE) {
                    // エンドレコード: 1バイト文字のみ全体変換
                    converted = convertHeaderRecord(record, bytesRead);
                    endCount++;
                    
                } else {
                    // 不明なレコード区分: そのまま出力
                    logger.warn("不明なレコード区分: 0x{} (レコード番号: {})", 
                        String.format("%02X", recordType), recordCount);
                    converted = new byte[bytesRead];
                    System.arraycopy(record, 0, converted, 0, bytesRead);
                }
                
                bos.write(converted);
            }
        }
        
        logger.info("レコード処理完了: 総数={}, ヘッダー={}, データ={}, トレーラー={}, エンド={}", 
            recordCount, headerCount, dataCount, trailerCount, endCount);
    }
    
    /**
     * シンプルなレコードを変換する（文字数ベース）。
     * ヘッダー、トレーラー、エンドレコード用。
     * 
     * @param line レコード文字列
     * @return 変換後バイト配列
     */
    private byte[] convertSimpleRecord(String line) {
        // 文字列として全体変換
        return CodeConverter.convertCharset(
            line.getBytes(Charset.forName(param.getSourceCharsetSingle())),
            param.getSourceCharsetSingle(),
            param.getTargetCharsetSingle()
        );
    }
    
    /**
     * データレコードを変換する（文字数ベース）。
     * 
     * @param line レコード文字列
     * @return 変換後バイト配列
     */
    private byte[] convertDataRecordCharacterBased(String line) {
        // データ種別を取得（2文字目、0-based indexでは1）
        char dataType = line.charAt(1);
        
        // 作業用バッファ
        ByteArrayOutputStream baos = new ByteArrayOutputStream(line.length() * 3);
        
        try {
            // データ種別に応じた漢字項目定義を取得
            KanjiFieldDefinition[] defs = KANJI_FIELD_DEFS.get((byte) dataType);
            if (defs == null) {
                logger.debug("データ種別 {} の漢字項目定義が存在しないため、1バイト文字のみ変換", dataType);
                // 定義がない場合は全体を1バイト文字として変換
                return convertSimpleRecord(line);
            }
            
            int currentPos = 0;
            
            for (KanjiFieldDefinition def : defs) {
                // 漢字項目の前の1バイト文字部分を変換
                if (currentPos < def.startPos) {
                    String singleByteField = line.substring(currentPos, Math.min(def.startPos, line.length()));
                    byte[] converted = CodeConverter.convertCharset(
                        singleByteField.getBytes(Charset.forName(param.getSourceCharsetSingle())),
                        param.getSourceCharsetSingle(),
                        param.getTargetCharsetSingle()
                    );
                    baos.write(converted);
                }
                
                // 漢字項目を変換
                int endPos = Math.min(def.endPos + 1, line.length());
                if (def.startPos < line.length()) {
                    String kanjiField = line.substring(def.startPos, endPos);
                    byte[] convertedKanji = CodeConverter.convertKanjiCharset(
                        kanjiField.getBytes(Charset.forName(param.getSourceCharsetDouble())),
                        param.getSourceCharsetDouble(),
                        param.getTargetCharsetDouble()
                    );
                    baos.write(convertedKanji);
                }
                
                currentPos = def.endPos + 1;
            }
            
            // 最後の漢字項目以降の1バイト文字部分を変換
            if (currentPos < line.length()) {
                String singleByteField = line.substring(currentPos);
                byte[] converted = CodeConverter.convertCharset(
                    singleByteField.getBytes(Charset.forName(param.getSourceCharsetSingle())),
                    param.getSourceCharsetSingle(),
                    param.getTargetCharsetSingle()
                );
                baos.write(converted);
            }
            
            return baos.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("データレコード変換エラー（文字数ベース）", e);
        }
    }
    
    /**
     * ヘッダー/トレーラー/エンドレコードを変換する（バイト数ベース）。
     * 
     * @param record レコードバイト配列
     * @param length 有効バイト長
     * @return 変換後バイト配列
     */
    private byte[] convertHeaderRecord(byte[] record, int length) {
        // レコード全体を1バイト文字として扱う
        byte[] target = new byte[length];
        System.arraycopy(record, 0, target, 0, length);
        
        return CodeConverter.convertCharset(
            target,
            param.getSourceCharsetSingle(),
            param.getTargetCharsetSingle()
        );
    }
    
    /**
     * データレコードを変換する（バイト数ベース）。
     * 
     * @param record レコードバイト配列
     * @param length 有効バイト長
     * @return 変換後バイト配列
     */
    private byte[] convertDataRecord(byte[] record, int length) {
        // データ種別を取得（2バイト目）
        byte dataType = record[1];
        
        // 作業用バッファ（最大長を確保）
        ByteArrayOutputStream baos = new ByteArrayOutputStream(length * 2);
        
        try {
            // データ種別に応じた漢字項目定義を取得
            KanjiFieldDefinition[] defs = KANJI_FIELD_DEFS.get(dataType);
            if (defs == null) {
                logger.debug("データ種別 {} の漢字項目定義が存在しないため、1バイト文字のみ変換", dataType);
                // 定義がない場合は全体を1バイト文字として変換
                return CodeConverter.convertCharset(
                    record,
                    param.getSourceCharsetSingle(),
                    param.getTargetCharsetSingle()
                );
            }
            
            int currentPos = 0;
            
            for (KanjiFieldDefinition def : defs) {
                // 漢字項目の前の1バイト文字部分を変換
                if (currentPos < def.startPos) {
                    byte[] singleByteField = extractBytes(record, currentPos, def.startPos - 1);
                    byte[] converted = CodeConverter.convertCharset(
                        singleByteField,
                        param.getSourceCharsetSingle(),
                        param.getTargetCharsetSingle()
                    );
                    baos.write(converted);
                }
                
                // 漢字項目を変換
                byte[] kanjiField = extractBytes(record, def.startPos, def.endPos);
                byte[] convertedKanji = CodeConverter.convertKanjiCharset(
                    kanjiField,
                    param.getSourceCharsetDouble(),
                    param.getTargetCharsetDouble()
                );
                baos.write(convertedKanji);
                
                currentPos = def.endPos + 1;
            }
            
            // 最後の漢字項目以降の1バイト文字部分を変換
            if (currentPos < length) {
                byte[] singleByteField = extractBytes(record, currentPos, length - 1);
                byte[] converted = CodeConverter.convertCharset(
                    singleByteField,
                    param.getSourceCharsetSingle(),
                    param.getTargetCharsetSingle()
                );
                baos.write(converted);
            }
            
            return baos.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("データレコード変換エラー", e);
        }
    }
    
    /**
     * バイト配列から指定範囲を抽出する。
     * 
     * @param source ソースバイト配列
     * @param startPos 開始位置（0-based）
     * @param endPos 終了位置（0-based、inclusive）
     * @return 抽出されたバイト配列
     */
    private byte[] extractBytes(byte[] source, int startPos, int endPos) {
        int length = endPos - startPos + 1;
        if (length <= 0 || startPos >= source.length) {
            return new byte[0];
        }
        
        int actualLength = Math.min(length, source.length - startPos);
        byte[] result = new byte[actualLength];
        System.arraycopy(source, startPos, result, 0, actualLength);
        return result;
    }
    
    /**
     * 漢字項目定義クラス。
     */
    private static class KanjiFieldDefinition {
        final int startPos;  // 開始位置（0-based）
        final int endPos;    // 終了位置（0-based、inclusive）
        
        KanjiFieldDefinition(int startPos, int endPos) {
            this.startPos = startPos;
            this.endPos = endPos;
        }
    }
}
