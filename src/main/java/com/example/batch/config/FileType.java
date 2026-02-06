package com.example.batch.config;

/**
 * ファイルタイプを定義するEnum。
 * 各ファイルタイプごとにレコード長、漢字変換の有無、可変長の有無を保持。
 */
public enum FileType {
    /**
     * FILE_A: 1バイト文字のみ、ファイル全体一括変換
     */
    FILE_A(0, false, false),
    
    /**
     * FILE_B: 1バイト文字のみ、ファイル全体一括変換
     */
    FILE_B(0, false, false),
    
    /**
     * FILE_C: 380バイト固定長、1バイト+2バイト混合、部分変換
     */
    FILE_C(380, true, false),
    
    /**
     * FILE_D: 380バイト固定長、1バイト+2バイト混合、部分変換
     */
    FILE_D(380, true, false),
    
    /**
     * FILE_E: 可変長レコード、制御文字変換あり
     */
    FILE_E(0, false, true),
    
    /**
     * FILE_F: 可変長レコード、制御文字変換あり
     */
    FILE_F(0, false, true);

    private final int recordLength;
    private final boolean hasKanjiConversion;
    private final boolean isVariableLength;

    /**
     * コンストラクタ
     * 
     * @param recordLength レコード長(バイト)。可変長の場合は0
     * @param hasKanjiConversion 漢字(2バイト文字)変換が必要かどうか
     * @param isVariableLength 可変長レコードかどうか
     */
    FileType(int recordLength, boolean hasKanjiConversion, boolean isVariableLength) {
        this.recordLength = recordLength;
        this.hasKanjiConversion = hasKanjiConversion;
        this.isVariableLength = isVariableLength;
    }

    /**
     * レコード長を取得
     * 
     * @return レコード長(バイト)。可変長またはファイル全体変換の場合は0
     */
    public int getRecordLength() {
        return recordLength;
    }

    /**
     * 漢字変換が必要かどうかを取得
     * 
     * @return 漢字変換が必要な場合true
     */
    public boolean hasKanjiConversion() {
        return hasKanjiConversion;
    }

    /**
     * 可変長レコードかどうかを取得
     * 
     * @return 可変長レコードの場合true
     */
    public boolean isVariableLength() {
        return isVariableLength;
    }

    /**
     * 文字列からFileTypeを取得
     * 
     * @param fileId ファイルID文字列
     * @return 対応するFileType
     * @throws IllegalArgumentException 不正なファイルIDの場合
     */
    public static FileType fromString(String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            throw new IllegalArgumentException("File ID cannot be null or empty");
        }
        
        try {
            return FileType.valueOf(fileId.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid file ID: " + fileId + ". Must be one of: FILE_A, FILE_B, FILE_C, FILE_D, FILE_E, FILE_F",
                e
            );
        }
    }
}
