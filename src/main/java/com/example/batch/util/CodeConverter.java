package com.example.batch.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * 文字コード変換を行うUtilityクラス。
 * 
 * <p>
 * 通常の文字コード変換と2バイト文字（漢字）の変換を提供する。
 * ISO-2022-JPのESCシーケンス処理にも対応する。
 * </p>
 */
public class CodeConverter {
    private static final Logger logger = LoggerFactory.getLogger(CodeConverter.class);
    
    // ISO-2022-JP用ESCシーケンス
    private static final byte[] ESC_TO_JIS_KANJI = new byte[]{0x1B, 0x24, 0x42};  // ESC $ B (JIS X 0208へ)
    private static final byte[] ESC_TO_ASCII = new byte[]{0x1B, 0x28, 0x42};      // ESC ( B (ASCIIへ)
    
    /**
     * 通常の文字コード変換を行う。
     * 
     * @param sourceBytes 変換前バイト列
     * @param sourceCharset 変換元charset
     * @param targetCharset 変換先charset
     * @return 変換後バイト列
     */
    public static byte[] convertCharset(byte[] sourceBytes, String sourceCharset, String targetCharset) {
        if (sourceBytes == null || sourceBytes.length == 0) {
            return new byte[0];
        }
        
        logger.debug("文字コード変換開始: {} -> {} ({} bytes)", 
            sourceCharset, targetCharset, sourceBytes.length);
        
        try {
            Charset srcCharset = Charset.forName(sourceCharset);
            Charset tgtCharset = Charset.forName(targetCharset);
            
            // バイト列を文字列にデコード
            String str = new String(sourceBytes, srcCharset);
            
            // 文字列をターゲット文字コードでエンコード
            byte[] result = str.getBytes(tgtCharset);
            
            logger.debug("文字コード変換完了: {} bytes -> {} bytes", 
                sourceBytes.length, result.length);
            
            return result;
        } catch (Exception e) {
            logger.error("文字コード変換エラー: {} -> {}", sourceCharset, targetCharset, e);
            throw new RuntimeException("文字コード変換に失敗しました", e);
        }
    }
    
    /**
     * 2バイト文字（漢字）の文字コード変換を行う。
     * 
     * <p>
     * ISO-2022-JPの場合、ESCシーケンスの付与・除去を行う。
     * </p>
     * 
     * @param sourceBytes 変換前バイト列（2バイト文字のみ）
     * @param sourceCharset 変換元charset
     * @param targetCharset 変換先charset
     * @return 変換後バイト列
     */
    public static byte[] convertKanjiCharset(byte[] sourceBytes, String sourceCharset, String targetCharset) {
        if (sourceBytes == null || sourceBytes.length == 0) {
            return new byte[0];
        }
        
        logger.debug("漢字文字コード変換開始: {} -> {} ({} bytes)", 
            sourceCharset, targetCharset, sourceBytes.length);
        
        try {
            byte[] processedSource = sourceBytes;
            
            // 変換元がISO-2022-JPの場合、ESCシーケンスを付与
            if (isIso2022Jp(sourceCharset)) {
                processedSource = addEscSequence(sourceBytes);
                logger.debug("ESCシーケンス付与: {} bytes -> {} bytes", 
                    sourceBytes.length, processedSource.length);
            }
            
            // 文字コード変換実行
            Charset srcCharset = Charset.forName(sourceCharset);
            Charset tgtCharset = Charset.forName(targetCharset);
            
            String str = new String(processedSource, srcCharset);
            byte[] converted = str.getBytes(tgtCharset);
            
            // 変換先がISO-2022-JPの場合、ESCシーケンスを除去
            byte[] result = converted;
            if (isIso2022Jp(targetCharset)) {
                result = removeEscSequence(converted);
                logger.debug("ESCシーケンス除去: {} bytes -> {} bytes", 
                    converted.length, result.length);
            }
            
            logger.debug("漢字文字コード変換完了: {} bytes -> {} bytes", 
                sourceBytes.length, result.length);
            
            return result;
        } catch (Exception e) {
            logger.error("漢字文字コード変換エラー: {} -> {}", sourceCharset, targetCharset, e);
            throw new RuntimeException("漢字文字コード変換に失敗しました", e);
        }
    }
    
    /**
     * ISO-2022-JP判定。
     * 
     * @param charset 文字コード名
     * @return ISO-2022-JPの場合true
     */
    private static boolean isIso2022Jp(String charset) {
        return charset != null && 
               charset.toUpperCase().replace("-", "").equals("ISO2022JP");
    }
    
    /**
     * JIS X 0208の2バイトコードにESCシーケンスを付与する。
     * 
     * @param kanjiBytes JIS X 0208の2バイトコード列
     * @return ESCシーケンス付きバイト列
     */
    private static byte[] addEscSequence(byte[] kanjiBytes) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try {
            // 漢字モードへのESCシーケンス
            baos.write(ESC_TO_JIS_KANJI);
            
            // 漢字データ
            baos.write(kanjiBytes);
            
            // ASCIIモードへのESCシーケンス
            baos.write(ESC_TO_ASCII);
            
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("ESCシーケンス付与に失敗しました", e);
        }
    }
    
    /**
     * ISO-2022-JPのバイト列からESCシーケンスを除去する。
     * 
     * @param iso2022JpBytes ISO-2022-JPバイト列
     * @return ESCシーケンス除去後の2バイトコード列
     */
    private static byte[] removeEscSequence(byte[] iso2022JpBytes) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        int i = 0;
        while (i < iso2022JpBytes.length) {
            // ESCシーケンスの検出
            if (iso2022JpBytes[i] == 0x1B && i + 2 < iso2022JpBytes.length) {
                // ESCシーケンスをスキップ
                i += 3;
            } else {
                // 通常のバイトを出力
                baos.write(iso2022JpBytes[i]);
                i++;
            }
        }
        
        return baos.toByteArray();
    }
}