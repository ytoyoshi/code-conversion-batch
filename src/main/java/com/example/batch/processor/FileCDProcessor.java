package com.example.batch.processor;

import com.example.batch.config.BatchParameters;
import com.example.batch.config.FileType;
import com.example.batch.model.FieldDefinition;
import com.example.batch.util.CodeConverter;

import java.io.ByteArrayOutputStream;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * FILE_C, FILE_D用のレコードプロセッサ。
 * 1バイト文字と2バイト文字(漢字)が混合する380バイト固定長ファイルを部分変換。
 */
public class FileCDProcessor extends RecordProcessor {

    private static final byte HEADER_RECORD_TYPE = 0x31;  // '1'
    private static final byte DATA_RECORD_TYPE = 0x32;    // '2'
    
    // データ種別
    private static final byte DATA_TYPE_1 = 0x31;  // '1'
    private static final byte DATA_TYPE_2 = 0x32;  // '2'

    /**
     * コンストラクタ
     * 
     * @param parameters バッチパラメータ
     * @param fileType ファイルタイプ(FILE_C または FILE_D)
     */
    public FileCDProcessor(BatchParameters parameters, FileType fileType) {
        super(parameters, fileType);
        
        if (fileType != FileType.FILE_C && fileType != FileType.FILE_D) {
            throw new IllegalArgumentException(
                "FileCDProcessor only supports FILE_C and FILE_D, but got: " + fileType);
        }
    }

    /**
     * レコードを変換。
     * 先頭1バイト目でヘッダー/データレコードを判定し、適切な処理を実施。
     * 
     * @param record 入力レコード(380バイト or 380文字)
     * @return 変換後のレコード
     * @throws CharacterCodingException 文字コード変換エラー
     */
    @Override
    public byte[] processRecord(byte[] record) throws CharacterCodingException {
        
        LOGGER.debug("Processing {} record: {} bytes", fileType, record.length);

        // UTF-8の場合、文字列として扱う
        if (isUtf8Source()) {
            return processUtf8Record(record);
        } else {
            return processByteRecord(record);
        }
    }

    /**
     * UTF-8レコードを処理(文字ベース)
     */
    private byte[] processUtf8Record(byte[] record) throws CharacterCodingException {
        // UTF-8として文字列に変換
        String recordStr = new String(record, parameters.getSourceCharsetSingle());
        
        if (recordStr.length() < 1) {
            throw new IllegalArgumentException("Record is too short");
        }

        // 先頭1文字目でレコード種別を判定
        char recordType = recordStr.charAt(0);
        
        if (recordType == '1') {
            return processHeaderRecord(record);
        } else if (recordType == '2') {
            return processDataRecordUtf8(recordStr);
        } else {
            throw new IllegalArgumentException("Invalid record type: " + recordType);
        }
    }

    /**
     * バイトベースのレコードを処理(JIS/EBCDIC)
     */
    private byte[] processByteRecord(byte[] record) throws CharacterCodingException {
        
        if (record.length != fileType.getRecordLength()) {
            throw new IllegalArgumentException(
                "Invalid record length for " + fileType + ": expected " + 
                fileType.getRecordLength() + " bytes, got " + record.length + " bytes");
        }

        // 先頭1バイト目でレコード種別を判定
        byte recordType = record[0];
        
        if (recordType == HEADER_RECORD_TYPE) {
            return processHeaderRecord(record);
        } else if (recordType == DATA_RECORD_TYPE) {
            return processDataRecordByte(record);
        } else {
            throw new IllegalArgumentException(
                "Invalid record type: 0x" + Integer.toHexString(recordType & 0xFF));
        }
    }

    /**
     * ヘッダーレコードを処理(全体を1バイト文字として変換)
     */
    private byte[] processHeaderRecord(byte[] record) throws CharacterCodingException {
        LOGGER.debug("Processing header record");
        
        return CodeConverter.convertCharset(
            record,
            parameters.getSourceCharsetSingle(),
            parameters.getTargetCharsetSingle()
        );
    }

    /**
     * データレコードを処理(UTF-8)
     */
    private byte[] processDataRecordUtf8(String recordStr) throws CharacterCodingException {
        LOGGER.debug("Processing data record (UTF-8): {} chars", recordStr.length());
        
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        
        try {
            // データ種別を取得(2文字目、UTF-8なので1文字=1バイト想定)
            byte dataType = DATA_TYPE_1;  // デフォルト
            if (recordStr.length() >= 2) {
                dataType = (byte) recordStr.charAt(1);
            }
            
            // 漢字フィールド定義を取得
            List<FieldDefinition> kanjiFields = getKanjiFieldDefinitionsUtf8(dataType);
            
            // 現在の文字位置
            int currentCharPos = 0;
            
            for (FieldDefinition field : kanjiFields) {
                // フィールドの前の部分(1バイト文字)
                if (currentCharPos < field.getStartCharPosition()) {
                    String beforeField = recordStr.substring(currentCharPos, field.getStartCharPosition());
                    byte[] converted = CodeConverter.convertCharset(
                        beforeField.getBytes(parameters.getSourceCharsetSingle()),
                        parameters.getSourceCharsetSingle(),
                        parameters.getTargetCharsetSingle()
                    );
                    result.write(converted);
                }
                
                // 漢字フィールドを抽出して変換
                int endPos = Math.min(
                    field.getStartCharPosition() + field.getLengthInChars(),
                    recordStr.length()
                );
                String kanjiField = recordStr.substring(field.getStartCharPosition(), endPos);
                
                // UTF-8には漢字が含まれないため、実際は1バイト文字として変換
                byte[] converted = CodeConverter.convertCharset(
                    kanjiField.getBytes(parameters.getSourceCharsetSingle()),
                    parameters.getSourceCharsetSingle(),
                    parameters.getTargetCharsetSingle()
                );
                result.write(converted);
                
                currentCharPos = endPos;
            }
            
            // 残りの部分(1バイト文字)
            if (currentCharPos < recordStr.length()) {
                String remaining = recordStr.substring(currentCharPos);
                byte[] converted = CodeConverter.convertCharset(
                    remaining.getBytes(parameters.getSourceCharsetSingle()),
                    parameters.getSourceCharsetSingle(),
                    parameters.getTargetCharsetSingle()
                );
                result.write(converted);
            }
            
            return result.toByteArray();
            
        } catch (Exception e) {
            throw new CharacterCodingException();
        }
    }

    /**
     * データレコードを処理(バイトベース: JIS/EBCDIC)
     */
    private byte[] processDataRecordByte(byte[] record) throws CharacterCodingException {
        LOGGER.debug("Processing data record (byte-based)");
        
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        
        try {
            // データ種別を取得(2バイト目)
            byte dataType = record.length >= 2 ? record[1] : DATA_TYPE_1;
            
            // 漢字フィールド定義を取得
            List<FieldDefinition> kanjiFields = getKanjiFieldDefinitionsByte(dataType);
            
            // 現在のバイト位置
            int currentBytePos = 0;
            
            for (FieldDefinition field : kanjiFields) {
                // フィールドの前の部分(1バイト文字)
                if (currentBytePos < field.getStartBytePosition()) {
                    byte[] beforeField = extractByBytePosition(
                        record, currentBytePos, field.getStartBytePosition() - 1
                    );
                    byte[] converted = CodeConverter.convertCharset(
                        beforeField,
                        parameters.getSourceCharsetSingle(),
                        parameters.getTargetCharsetSingle()
                    );
                    result.write(converted);
                }
                
                // 漢字フィールドを抽出して変換
                byte[] kanjiField = extractByBytePosition(
                    record, field.getStartBytePosition(), field.getEndBytePosition()
                );
                
                byte[] converted = CodeConverter.convertDoubleByteCharset(
                    kanjiField,
                    parameters.getSourceCharsetDouble(),
                    parameters.getTargetCharsetDouble()
                );
                result.write(converted);
                
                currentBytePos = field.getEndBytePosition() + 1;
            }
            
            // 残りの部分(1バイト文字)
            if (currentBytePos < record.length) {
                byte[] remaining = extractByBytePosition(record, currentBytePos, record.length - 1);
                byte[] converted = CodeConverter.convertCharset(
                    remaining,
                    parameters.getSourceCharsetSingle(),
                    parameters.getTargetCharsetSingle()
                );
                result.write(converted);
            }
            
            return result.toByteArray();
            
        } catch (Exception e) {
            throw new CharacterCodingException();
        }
    }

    /**
     * 漢字フィールド定義を取得(UTF-8用、文字ベース)
     */
    private List<FieldDefinition> getKanjiFieldDefinitionsUtf8(byte dataType) {
        List<FieldDefinition> fields = new ArrayList<>();
        
        if (fileType == FileType.FILE_C) {
            // FILE_C: データレコード内の数箇所
            fields.add(FieldDefinition.createCharBased(50, 25));   // 50-74文字目
            fields.add(FieldDefinition.createCharBased(150, 25));  // 150-174文字目
            fields.add(FieldDefinition.createCharBased(300, 25));  // 300-324文字目
            
        } else if (fileType == FileType.FILE_D) {
            // FILE_D: データ種別により位置が変わる
            if (dataType == DATA_TYPE_1) {
                fields.add(FieldDefinition.createCharBased(100, 25));  // 100-124文字目
                fields.add(FieldDefinition.createCharBased(200, 25));  // 200-224文字目
            } else if (dataType == DATA_TYPE_2) {
                fields.add(FieldDefinition.createCharBased(120, 25));  // 120-144文字目
                fields.add(FieldDefinition.createCharBased(250, 25));  // 250-274文字目
            }
        }
        
        return fields;
    }

    /**
     * 漢字フィールド定義を取得(バイトベース: JIS/EBCDIC)
     */
    private List<FieldDefinition> getKanjiFieldDefinitionsByte(byte dataType) {
        List<FieldDefinition> fields = new ArrayList<>();
        
        if (fileType == FileType.FILE_C) {
            // FILE_C: データレコード内の数箇所(各25文字=50バイト)
            fields.add(new FieldDefinition(50, 99));    // 50-99バイト目
            fields.add(new FieldDefinition(150, 199));  // 150-199バイト目
            fields.add(new FieldDefinition(300, 349));  // 300-349バイト目
            
        } else if (fileType == FileType.FILE_D) {
            // FILE_D: データ種別により位置が変わる(各25文字=50バイト)
            if (dataType == DATA_TYPE_1) {
                fields.add(new FieldDefinition(100, 149));  // 100-149バイト目
                fields.add(new FieldDefinition(200, 249));  // 200-249バイト目
            } else if (dataType == DATA_TYPE_2) {
                fields.add(new FieldDefinition(120, 169));  // 120-169バイト目
                fields.add(new FieldDefinition(250, 299));  // 250-299バイト目
            }
        }
        
        return fields;
    }
}
