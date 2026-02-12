package com.example.batch.model;

/**
 * 漢字項目定義クラス。
 * 
 * <p>
 * データレコード内の漢字項目の位置を定義する。
 * UTF-8の場合は文字位置、JIS/EBCDICの場合はバイト位置として解釈される。
 * </p>
 */
public class KanjiFieldDefinition {
    private final int startPos;  // 開始位置（0-based）
    private final int endPos;    // 終了位置（0-based、inclusive）
    
    /**
     * コンストラクタ。
     * 
     * @param startPos 開始位置（0-based）
     * @param endPos 終了位置（0-based、inclusive）
     */
    public KanjiFieldDefinition(int startPos, int endPos) {
        this.startPos = startPos;
        this.endPos = endPos;
    }
    
    /**
     * 開始位置を取得する。
     * 
     * @return 開始位置（0-based）
     */
    public int getStartPos() {
        return startPos;
    }
    
    /**
     * 終了位置を取得する。
     * 
     * @return 終了位置（0-based、inclusive）
     */
    public int getEndPos() {
        return endPos;
    }
}
