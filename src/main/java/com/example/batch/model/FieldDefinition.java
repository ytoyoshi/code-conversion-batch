package com.example.batch.model;

/**
 * レコード内のフィールド定義を保持するクラス。
 * バイト位置ベース(JIS/EBCDIC)と文字位置ベース(UTF-8)の両方に対応。
 */
public class FieldDefinition {
    
    // バイト位置ベースの定義(JIS/EBCDIC用)
    private final int startBytePosition;
    private final int endBytePosition;
    
    // 文字位置ベースの定義(UTF-8用)
    private final int startCharPosition;
    private final int lengthInChars;
    
    // 定義タイプ
    private final DefinitionType type;
    
    /**
     * 定義タイプ
     */
    public enum DefinitionType {
        BYTE_BASED,  // バイト位置ベース
        CHAR_BASED   // 文字位置ベース
    }

    /**
     * バイト位置ベースのコンストラクタ(JIS/EBCDIC用)
     * 
     * @param startBytePosition 開始バイト位置(0始まり)
     * @param endBytePosition 終了バイト位置(0始まり、この位置を含む)
     */
    public FieldDefinition(int startBytePosition, int endBytePosition) {
        if (startBytePosition < 0 || endBytePosition < startBytePosition) {
            throw new IllegalArgumentException(
                "Invalid byte position: start=" + startBytePosition + ", end=" + endBytePosition);
        }
        
        this.startBytePosition = startBytePosition;
        this.endBytePosition = endBytePosition;
        this.startCharPosition = 0;
        this.lengthInChars = 0;
        this.type = DefinitionType.BYTE_BASED;
    }

    /**
     * 文字位置ベースのコンストラクタ(UTF-8用)
     * 
     * @param startCharPosition 開始文字位置(0始まり)
     * @param lengthInChars 文字数
     */
    public static FieldDefinition createCharBased(int startCharPosition, int lengthInChars) {
        return new FieldDefinition(startCharPosition, lengthInChars, true);
    }

    /**
     * 内部用コンストラクタ(文字ベース)
     */
    private FieldDefinition(int startCharPosition, int lengthInChars, boolean isCharBased) {
        if (startCharPosition < 0 || lengthInChars <= 0) {
            throw new IllegalArgumentException(
                "Invalid char position: start=" + startCharPosition + ", length=" + lengthInChars);
        }
        
        this.startCharPosition = startCharPosition;
        this.lengthInChars = lengthInChars;
        this.startBytePosition = 0;
        this.endBytePosition = 0;
        this.type = DefinitionType.CHAR_BASED;
    }

    /**
     * 定義タイプを取得
     * 
     * @return 定義タイプ
     */
    public DefinitionType getType() {
        return type;
    }

    /**
     * 開始バイト位置を取得(バイトベースの場合)
     * 
     * @return 開始バイト位置
     */
    public int getStartBytePosition() {
        if (type != DefinitionType.BYTE_BASED) {
            throw new IllegalStateException("This definition is not byte-based");
        }
        return startBytePosition;
    }

    /**
     * 終了バイト位置を取得(バイトベースの場合)
     * 
     * @return 終了バイト位置
     */
    public int getEndBytePosition() {
        if (type != DefinitionType.BYTE_BASED) {
            throw new IllegalStateException("This definition is not byte-based");
        }
        return endBytePosition;
    }

    /**
     * 開始文字位置を取得(文字ベースの場合)
     * 
     * @return 開始文字位置
     */
    public int getStartCharPosition() {
        if (type != DefinitionType.CHAR_BASED) {
            throw new IllegalStateException("This definition is not char-based");
        }
        return startCharPosition;
    }

    /**
     * 文字数を取得(文字ベースの場合)
     * 
     * @return 文字数
     */
    public int getLengthInChars() {
        if (type != DefinitionType.CHAR_BASED) {
            throw new IllegalStateException("This definition is not char-based");
        }
        return lengthInChars;
    }

    /**
     * バイト長を計算(バイトベースの場合)
     * 
     * @return バイト長
     */
    public int getLengthInBytes() {
        if (type != DefinitionType.BYTE_BASED) {
            throw new IllegalStateException("This definition is not byte-based");
        }
        return endBytePosition - startBytePosition + 1;
    }

    @Override
    public String toString() {
        if (type == DefinitionType.BYTE_BASED) {
            return "FieldDefinition[BYTE: " + startBytePosition + "-" + endBytePosition + 
                   " (" + getLengthInBytes() + " bytes)]";
        } else {
            return "FieldDefinition[CHAR: pos=" + startCharPosition + 
                   ", len=" + lengthInChars + " chars]";
        }
    }
}
