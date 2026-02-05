package com.example.batch.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharacterCodingException;

/**
 * 文字コード変換を行うUtilityクラス。
 * ファイルのレイアウト情報は持たず、純粋な文字コード変換機能のみを提供。
 */
public class CodeConverter {

    // ISO-2022-JP用エスケープシーケンス
    private static final byte[] ESC_SEQ_ASCII = {0x1B, 0x28, 0x42};      // ESC ( B
    private static final byte[] ESC_SEQ_KANJI_IN = {0x1B, 0x24, 0x42};   // ESC $ B
    private static final byte[] ESC_SEQ_KANJI_OUT = {0x1B, 0x28, 0x42};  // ESC ( B

    // 制御文字定義
    private static final byte CONTROL_CHAR_EBCDIC = (byte) 0xB4;  // EBCDIC 'u'
    private static final byte CONTROL_CHAR_JIS_UTF8 = (byte) 0x74; // JIS/UTF-8 't'

    /**
     * 通常の文字コード変換を実施。
     * 1バイト文字の変換に使用。
     * 
     * @param source 変換前のバイト列
     * @param sourceCharset 変換元Charset
     * @param targetCharset 変換先Charset
     * @return 変換後のバイト列
     * @throws CharacterCodingException 変換エラー
     */
    public static byte[] convertCharset(byte[] source, Charset sourceCharset, Charset targetCharset) 
            throws CharacterCodingException {
        
        if (source == null || source.length == 0) {
            return new byte[0];
        }

        // 制御文字変換が必要な場合(EBCDIC ⇔ JIS/UTF-8)
        byte[] processedSource = source;
        if (needsControlCharConversion(sourceCharset, targetCharset)) {
            if (isEbcdic(sourceCharset)) {
                // EBCDIC → JIS/UTF-8: 0xB4 → 0x74
                processedSource = convertControlCharacter(source, CONTROL_CHAR_EBCDIC, CONTROL_CHAR_JIS_UTF8);
            } else if (isEbcdic(targetCharset)) {
                // JIS/UTF-8 → EBCDIC: 0x74 → 0xB4
                processedSource = convertControlCharacter(source, CONTROL_CHAR_JIS_UTF8, CONTROL_CHAR_EBCDIC);
            }
        }

        // デコーダ・エンコーダの設定
        CharsetDecoder decoder = sourceCharset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        
        CharsetEncoder encoder = targetCharset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

        // 変換実行
        ByteBuffer sourceBuffer = ByteBuffer.wrap(processedSource);
        CharBuffer charBuffer = decoder.decode(sourceBuffer);
        ByteBuffer targetBuffer = encoder.encode(charBuffer);

        byte[] result = new byte[targetBuffer.remaining()];
        targetBuffer.get(result);
        
        return result;
    }

    /**
     * 2バイト文字(漢字)の文字コード変換を実施。
     * ISO-2022-JPの場合、ESCシーケンスの付与/除去を行う。
     * 
     * @param source 変換前のバイト列(ISO-2022-JPの場合はESCシーケンスなし)
     * @param sourceCharset 変換元Charset
     * @param targetCharset 変換先Charset
     * @return 変換後のバイト列(ISO-2022-JPの場合はESCシーケンスなし)
     * @throws CharacterCodingException 変換エラー
     */
    public static byte[] convertDoubleByteCharset(byte[] source, Charset sourceCharset, Charset targetCharset) 
            throws CharacterCodingException {
        
        if (source == null || source.length == 0) {
            return new byte[0];
        }

        byte[] processedSource = source;
        
        // 変換元がISO-2022-JPの場合、ESCシーケンスを付与
        if (isIso2022Jp(sourceCharset)) {
            processedSource = addEscapeSequence(source);
        }

        // デコーダ・エンコーダの設定
        CharsetDecoder decoder = sourceCharset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        
        CharsetEncoder encoder = targetCharset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

        // 変換実行
        ByteBuffer sourceBuffer = ByteBuffer.wrap(processedSource);
        CharBuffer charBuffer = decoder.decode(sourceBuffer);
        ByteBuffer targetBuffer = encoder.encode(charBuffer);

        byte[] result = new byte[targetBuffer.remaining()];
        targetBuffer.get(result);

        // 変換先がISO-2022-JPの場合、ESCシーケンスを除去
        if (isIso2022Jp(targetCharset)) {
            result = removeEscapeSequence(result);
        }
        
        return result;
    }

    /**
     * ISO-2022-JP用のエスケープシーケンスを付与。
     * ESC $ B [漢字データ] ESC ( B の形式にする。
     * 
     * @param data エスケープシーケンスなしの漢字データ
     * @return エスケープシーケンス付きのデータ
     */
    private static byte[] addEscapeSequence(byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(data.length + ESC_SEQ_KANJI_IN.length + ESC_SEQ_KANJI_OUT.length);
        buffer.put(ESC_SEQ_KANJI_IN);   // ESC $ B (漢字開始)
        buffer.put(data);                // 漢字データ
        buffer.put(ESC_SEQ_KANJI_OUT);  // ESC ( B (ASCII復帰)
        
        return buffer.array();
    }

    /**
     * ISO-2022-JPのエスケープシーケンスを除去。
     * ESC $ B と ESC ( B を取り除き、純粋な漢字データのみを返す。
     * 
     * @param data エスケープシーケンス付きのデータ
     * @return エスケープシーケンスを除去したデータ
     */
    private static byte[] removeEscapeSequence(byte[] data) {
        ByteBuffer result = ByteBuffer.allocate(data.length);
        
        int i = 0;
        while (i < data.length) {
            // ESCシーケンスの検出
            if (i + 2 < data.length && data[i] == 0x1B) {
                // ESC $ B (漢字開始) または ESC ( B (ASCII復帰) をスキップ
                if ((data[i + 1] == 0x24 && data[i + 2] == 0x42) ||
                    (data[i + 1] == 0x28 && data[i + 2] == 0x42)) {
                    i += 3;
                    continue;
                }
            }
            
            result.put(data[i]);
            i++;
        }
        
        result.flip();
        byte[] output = new byte[result.remaining()];
        result.get(output);
        
        return output;
    }

    /**
     * 制御文字を変換。
     * 
     * @param data 変換対象データ
     * @param fromChar 変換元制御文字
     * @param toChar 変換先制御文字
     * @return 変換後のデータ
     */
    private static byte[] convertControlCharacter(byte[] data, byte fromChar, byte toChar) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (data[i] == fromChar) ? toChar : data[i];
        }
        return result;
    }

    /**
     * 制御文字変換が必要かチェック
     */
    private static boolean needsControlCharConversion(Charset sourceCharset, Charset targetCharset) {
        return (isEbcdic(sourceCharset) && !isEbcdic(targetCharset)) ||
               (!isEbcdic(sourceCharset) && isEbcdic(targetCharset));
    }

    /**
     * ISO-2022-JPかどうかを判定
     */
    private static boolean isIso2022Jp(Charset charset) {
        return charset.name().toUpperCase().contains("ISO-2022-JP");
    }

    /**
     * EBCDICかどうかを判定
     */
    private static boolean isEbcdic(Charset charset) {
        String name = charset.name().toUpperCase();
        return name.contains("CP930") || name.contains("IBM930") || name.contains("EBCDIC");
    }
}
