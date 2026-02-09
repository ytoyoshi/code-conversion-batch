package com.example.batch.constant;

/**
 * ファイル識別子を定義するEnum。
 * 
 * <p>
 * 本アプリケーションでサポートするファイルタイプを定義する。
 * </p>
 */
public enum FileType {
    /**
     * FILE_A: 1バイト文字のみ、210バイト固定長
     */
    FILE_A,
    
    /**
     * FILE_B: 1バイト文字のみ、210バイト固定長
     */
    FILE_B,
    
    /**
     * FILE_C: 1バイト・2バイト混合、380バイト固定長
     */
    FILE_C,
    
    /**
     * FILE_D: 1バイト・2バイト混合、380バイト固定長
     */
    FILE_D,
    
    /**
     * FILE_E: 可変長レコード
     */
    FILE_E,
    
    /**
     * FILE_F: 可変長レコード
     */
    FILE_F;
    
    /**
     * 文字列からFileType enumを取得する。
     * 
     * @param value ファイルタイプ文字列
     * @return FileType enum
     * @throws IllegalArgumentException 不正な値の場合
     */
    public static FileType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("ファイルIDが指定されていません");
        }
        
        try {
            return FileType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "不正なファイルIDです: " + value + 
                " (有効な値: FILE_A, FILE_B, FILE_C, FILE_D, FILE_E, FILE_F)");
        }
    }
}
