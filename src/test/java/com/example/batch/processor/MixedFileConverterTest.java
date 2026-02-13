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
 * MixedFileConverterのテストクラス（FILE_C/FILE_D用）。
 */
public class MixedFileConverterTest {

    private Path testDir;
    private Path paramFile;
    private Path inputFile;
    private Path outputFile;

    @Before
    public void setUp() throws IOException {
        // テスト用の一時ディレクトリを作成
        testDir = Files.createTempDirectory("mixed-file-test-");
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
    public void testMixedFileConversion_UTF8ToJIS_CharacterBased() throws Exception {
        // テスト用の入力ファイルを作成（UTF-8、文字数ベース）
        createTestInputFile_FILE_C_UTF8();

        // パラメータファイルを作成
        String paramContent = String.format(
            "input.file.path=%s\n" +
            "output.file.path=%s\n" +
            "source.charset.single=UTF-8\n" +
            "source.charset.double=UTF-8\n" +
            "target.charset.single=JIS_X0201\n" +
            "target.charset.double=ISO-2022-JP\n" +
            "file.id=FILE_C\n",
            inputFile.toAbsolutePath(),
            outputFile.toAbsolutePath()
        );
        Files.write(paramFile, paramContent.getBytes("UTF-8"));

        // パラメータ読み込み
        BatchParameter param = BatchParameter.load(paramFile.toString());

        // MixedFileConverterを実行
        MixedFileConverter converter = new MixedFileConverter(param);
        converter.execute();

        // 出力ファイルが生成されたことを確認
        assertTrue("出力ファイルが生成されていません", Files.exists(outputFile));

        // 出力ファイルの内容を確認
        byte[] outputBytes = Files.readAllBytes(outputFile);
        assertTrue("出力ファイルが空です", outputBytes.length > 0);

        // ヘッダーレコードのコード区分を確認
        assertEquals("ヘッダーレコードのコード区分が正しくありません", (byte)'0', outputBytes[5]);
    }

    @Test
    public void testMixedFileConversion_JISToUTF8_ByteBased() throws Exception {
        // テスト用の入力ファイルを作成（JIS、バイト数ベース）
        createTestInputFile_FILE_D_JIS();

        // パラメータファイルを作成
        String paramContent = String.format(
            "input.file.path=%s\n" +
            "output.file.path=%s\n" +
            "source.charset.single=JIS_X0201\n" +
            "source.charset.double=ISO-2022-JP\n" +
            "target.charset.single=UTF-8\n" +
            "target.charset.double=UTF-8\n" +
            "file.id=FILE_D\n",
            inputFile.toAbsolutePath(),
            outputFile.toAbsolutePath()
        );
        Files.write(paramFile, paramContent.getBytes("UTF-8"));

        // パラメータ読み込み
        BatchParameter param = BatchParameter.load(paramFile.toString());

        // MixedFileConverterを実行
        MixedFileConverter converter = new MixedFileConverter(param);
        converter.execute();

        // 出力ファイルが生成されたことを確認
        assertTrue("出力ファイルが生成されていません", Files.exists(outputFile));

        // 出力ファイルの内容を確認
        byte[] outputBytes = Files.readAllBytes(outputFile);
        assertTrue("出力ファイルが空です", outputBytes.length > 0);
    }

    @Test
    public void testMixedFileConversion_NoKanjiConversion() throws Exception {
        // テスト用の入力ファイルを作成（UTF-8）
        createTestInputFile_FILE_C_UTF8();

        // パラメータファイルを作成（2バイト文字コードが同一 = 漢字変換なし）
        String paramContent = String.format(
            "input.file.path=%s\n" +
            "output.file.path=%s\n" +
            "source.charset.single=UTF-8\n" +
            "source.charset.double=UTF-8\n" +
            "target.charset.single=UTF-8\n" +
            "target.charset.double=UTF-8\n" +
            "file.id=FILE_C\n",
            inputFile.toAbsolutePath(),
            outputFile.toAbsolutePath()
        );
        Files.write(paramFile, paramContent.getBytes("UTF-8"));

        // パラメータ読み込み
        BatchParameter param = BatchParameter.load(paramFile.toString());

        // 漢字変換フラグがfalseであることを確認
        assertFalse("漢字変換フラグが正しくありません", param.isConvertDoubleByteCharset());

        // MixedFileConverterを実行
        MixedFileConverter converter = new MixedFileConverter(param);
        converter.execute();

        // 出力ファイルが生成されたことを確認
        assertTrue("出力ファイルが生成されていません", Files.exists(outputFile));
    }

    /**
     * テスト用の入力ファイルを作成（FILE_C形式、UTF-8）。
     */
    private void createTestInputFile_FILE_C_UTF8() throws IOException {
        StringBuilder sb = new StringBuilder();

        // ヘッダーレコード（380文字）
        sb.append("1");           // レコード区分
        sb.append("H");           // データ種別
        sb.append("   ");         // 3-5文字目
        sb.append("0");           // 6文字目: コード区分（UTF-8用）
        for (int i = 6; i < 380; i++) {
            sb.append(" ");
        }

        // データレコード（データ種別='1'、350文字）
        sb.append("2");           // レコード区分
        sb.append("1");           // データ種別
        // 1バイト文字部分（3-49文字目）
        for (int i = 2; i < 49; i++) {
            sb.append("A");
        }
        // 漢字項目1（50-99文字目）- 仕様書の定義に基づく
        for (int i = 49; i < 99; i++) {
            sb.append("あ");      // 全角文字
        }
        // 1バイト文字部分（100-149文字目）
        for (int i = 99; i < 149; i++) {
            sb.append("B");
        }
        // 漢字項目2（150-199文字目）
        for (int i = 149; i < 199; i++) {
            sb.append("い");      // 全角文字
        }
        // 残りの1バイト文字部分（200-350文字目）
        for (int i = 199; i < 350; i++) {
            sb.append("C");
        }

        // エンドレコード（380文字）
        sb.append("9");           // レコード区分
        sb.append("E");           // データ種別
        for (int i = 2; i < 380; i++) {
            sb.append(" ");
        }

        Files.write(inputFile, sb.toString().getBytes("UTF-8"));
    }

    /**
     * テスト用の入力ファイルを作成（FILE_D形式、JIS_X0201）。
     */
    private void createTestInputFile_FILE_D_JIS() throws IOException {
        StringBuilder sb = new StringBuilder();

        // ヘッダーレコード（380バイト）
        sb.append("1");           // レコード区分
        sb.append("H");           // データ種別
        sb.append("   ");         // 3-5バイト目
        sb.append("0");           // 6バイト目: コード区分（JIS用）
        for (int i = 6; i < 380; i++) {
            sb.append(" ");
        }

        // データレコード（データ種別='2'、380バイト）
        sb.append("2");           // レコード区分
        sb.append("2");           // データ種別
        // 残りは1バイト文字で埋める
        for (int i = 2; i < 380; i++) {
            sb.append("D");
        }

        // トレーラーレコード（380バイト）
        sb.append("8");           // レコード区分
        sb.append("T");           // データ種別
        for (int i = 2; i < 380; i++) {
            sb.append(" ");
        }

        Files.write(inputFile, sb.toString().getBytes("JIS_X0201"));
    }
}
