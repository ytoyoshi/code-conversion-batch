package com.example.batch.processor;

import com.example.batch.param.BatchParameter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * WholeFileConverterのテストクラス（FILE_A/FILE_B用）。
 */
public class WholeFileConverterTest {

    private Path testDir;
    private Path paramFile;
    private Path inputFile;
    private Path outputFile;

    @Before
    public void setUp() throws IOException {
        // テスト用の一時ディレクトリを作成
        testDir = Files.createTempDirectory("whole-file-test-");
        paramFile = testDir.resolve("parameter.properties");
        inputFile = testDir.resolve("input.txt");
        outputFile = testDir.resolve("output.txt");
    }

    @After
    public void tearDown() throws IOException {
        // テスト用ファイルを削除
        if (Files.exists(paramFile)) Files.delete(paramFile);
        if (Files.exists(inputFile)) Files.delete(inputFile);
        if (Files.exists(outputFile)) Files.delete(outputFile);
        if (Files.exists(testDir)) Files.delete(testDir);
    }

    @Test
    public void testWholeFileConversion_UTF8ToJIS() throws Exception {
        // テスト用の入力ファイルを作成（210バイト × 4レコード）
        createTestInputFile_FILE_A();

        // パラメータファイルを作成
        String paramContent = String.format(
            "input.file.path=%s\n" +
            "output.file.path=%s\n" +
            "source.charset.single=UTF-8\n" +
            "target.charset.single=JIS_X0201\n" +
            "file.id=FILE_A\n",
            inputFile.toAbsolutePath(),
            outputFile.toAbsolutePath()
        );
        Files.write(paramFile, paramContent.getBytes("UTF-8"));

        // パラメータ読み込み
        BatchParameter param = BatchParameter.load(paramFile.toString());

        // WholeFileConverterを実行
        WholeFileConverter converter = new WholeFileConverter(param);
        converter.execute();

        // 出力ファイルが生成されたことを確認
        assertTrue("出力ファイルが生成されていません", Files.exists(outputFile));

        // 出力ファイルのサイズを確認（210バイト × 4 = 840バイト）
        long outputSize = Files.size(outputFile);
        assertEquals("出力ファイルサイズが正しくありません", 840L, outputSize);

        // 出力ファイルの内容を確認（ヘッダーレコードのコード区分）
        byte[] outputBytes = Files.readAllBytes(outputFile);
        // ヘッダーレコード（1バイト目が'1'）の6バイト目（0-indexで5）がJIS用の'0'であることを確認
        assertEquals("ヘッダーレコードのコード区分が正しくありません", (byte)'0', outputBytes[5]);
    }

    @Test
    public void testWholeFileConversion_JISToUTF8() throws Exception {
        // テスト用の入力ファイルを作成（JIS_X0201）
        createTestInputFile_FILE_A_JIS();

        // パラメータファイルを作成
        String paramContent = String.format(
            "input.file.path=%s\n" +
            "output.file.path=%s\n" +
            "source.charset.single=JIS_X0201\n" +
            "target.charset.single=UTF-8\n" +
            "file.id=FILE_B\n",
            inputFile.toAbsolutePath(),
            outputFile.toAbsolutePath()
        );
        Files.write(paramFile, paramContent.getBytes("UTF-8"));

        // パラメータ読み込み
        BatchParameter param = BatchParameter.load(paramFile.toString());

        // WholeFileConverterを実行
        WholeFileConverter converter = new WholeFileConverter(param);
        converter.execute();

        // 出力ファイルが生成されたことを確認
        assertTrue("出力ファイルが生成されていません", Files.exists(outputFile));

        // 出力ファイルの内容を確認
        byte[] outputBytes = Files.readAllBytes(outputFile);
        // ヘッダーレコード（1バイト目が'1'）の6バイト目（0-indexで5）がUTF-8用の'0'であることを確認
        assertEquals("ヘッダーレコードのコード区分が正しくありません", (byte)'0', outputBytes[5]);
    }

    /**
     * テスト用の入力ファイルを作成（FILE_A形式、UTF-8、210バイト固定長）。
     */
    private void createTestInputFile_FILE_A() throws IOException {
        StringBuilder sb = new StringBuilder();

        // ヘッダーレコード（210バイト）
        sb.append("1");           // レコード区分
        sb.append("H");           // データ種別（仮）
        sb.append("   ");         // 3-5バイト目
        sb.append("0");           // 6バイト目: コード区分（UTF-8用）
        for (int i = 6; i < 210; i++) {
            sb.append(" ");
        }

        // データレコード1（210バイト）
        sb.append("2");           // レコード区分
        sb.append("1");           // データ種別
        for (int i = 2; i < 210; i++) {
            sb.append("A");
        }

        // データレコード2（210バイト）
        sb.append("2");           // レコード区分
        sb.append("2");           // データ種別
        for (int i = 2; i < 210; i++) {
            sb.append("B");
        }

        // エンドレコード（210バイト）
        sb.append("9");           // レコード区分
        sb.append("E");           // データ種別（仮）
        for (int i = 2; i < 210; i++) {
            sb.append(" ");
        }

        Files.write(inputFile, sb.toString().getBytes("UTF-8"));
    }

    /**
     * テスト用の入力ファイルを作成（FILE_A形式、JIS_X0201、210バイト固定長）。
     */
    private void createTestInputFile_FILE_A_JIS() throws IOException {
        StringBuilder sb = new StringBuilder();

        // ヘッダーレコード（210バイト）
        sb.append("1");           // レコード区分
        sb.append("H");           // データ種別（仮）
        sb.append("   ");         // 3-5バイト目
        sb.append("0");           // 6バイト目: コード区分（JIS用）
        for (int i = 6; i < 210; i++) {
            sb.append(" ");
        }

        // データレコード1（210バイト）
        sb.append("2");           // レコード区分
        sb.append("1");           // データ種別
        for (int i = 2; i < 210; i++) {
            sb.append("C");
        }

        // トレーラーレコード（210バイト）
        sb.append("8");           // レコード区分
        sb.append("T");           // データ種別（仮）
        for (int i = 2; i < 210; i++) {
            sb.append(" ");
        }

        // エンドレコード（210バイト）
        sb.append("9");           // レコード区分
        sb.append("E");           // データ種別（仮）
        for (int i = 2; i < 210; i++) {
            sb.append(" ");
        }

        Files.write(inputFile, sb.toString().getBytes("JIS_X0201"));
    }
}
