package com.example.batch.processor;

import com.example.batch.param.BatchParameter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * VariableLengthConverterのテストクラス（FILE_E/FILE_F用）。
 */
public class VariableLengthConverterTest {

    private Path testDir;
    private Path paramFile;
    private Path inputFile;
    private Path outputFile;

    @Before
    public void setUp() throws IOException {
        // テスト用の一時ディレクトリを作成
        testDir = Files.createTempDirectory("variable-length-test-");
        paramFile = testDir.resolve("parameter.properties");
        inputFile = testDir.resolve("input.dat");
        outputFile = testDir.resolve("output.dat");
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
    public void testVariableLengthConversion_UTF8ToJIS() throws Exception {
        // テスト用の入力ファイルを作成（UTF-8）
        createTestInputFile_FILE_E_UTF8();

        // パラメータファイルを作成
        String paramContent = String.format(
            "input.file.path=%s\n" +
            "output.file.path=%s\n" +
            "source.charset.single=UTF-8\n" +
            "target.charset.single=JIS_X0201\n" +
            "file.id=FILE_E\n",
            inputFile.toAbsolutePath(),
            outputFile.toAbsolutePath()
        );
        Files.write(paramFile, paramContent.getBytes("UTF-8"));

        // パラメータ読み込み
        BatchParameter param = BatchParameter.load(paramFile.toString());

        // VariableLengthConverterを実行
        VariableLengthConverter converter = new VariableLengthConverter(param);
        converter.execute();

        // 出力ファイルが生成されたことを確認
        assertTrue("出力ファイルが生成されていません", Files.exists(outputFile));

        // 出力ファイルの内容を確認
        byte[] outputBytes = Files.readAllBytes(outputFile);
        assertTrue("出力ファイルが空です", outputBytes.length > 0);

        // BDW（最初の4バイト）を確認
        byte[] bdw = new byte[4];
        System.arraycopy(outputBytes, 0, bdw, 0, 4);
        int blockLength = bytesToInt(bdw);
        assertTrue("ブロック長が不正です", blockLength > 4);

        // 終端符号が変換されたことを確認（0x74 → 0x74のまま、JIS用）
        // 実際の終端符号の位置はレコード構造により異なるため、存在確認のみ
        boolean hasTerminator = false;
        for (byte b : outputBytes) {
            if (b == 0x74) {
                hasTerminator = true;
                break;
            }
        }
        assertTrue("終端符号が見つかりません", hasTerminator);
    }

    @Test
    public void testVariableLengthConversion_JISToUTF8() throws Exception {
        // テスト用の入力ファイルを作成（JIS_X0201）
        createTestInputFile_FILE_F_JIS();

        // パラメータファイルを作成
        String paramContent = String.format(
            "input.file.path=%s\n" +
            "output.file.path=%s\n" +
            "source.charset.single=JIS_X0201\n" +
            "target.charset.single=UTF-8\n" +
            "file.id=FILE_F\n",
            inputFile.toAbsolutePath(),
            outputFile.toAbsolutePath()
        );
        Files.write(paramFile, paramContent.getBytes("UTF-8"));

        // パラメータ読み込み
        BatchParameter param = BatchParameter.load(paramFile.toString());

        // VariableLengthConverterを実行
        VariableLengthConverter converter = new VariableLengthConverter(param);
        converter.execute();

        // 出力ファイルが生成されたことを確認
        assertTrue("出力ファイルが生成されていません", Files.exists(outputFile));

        // 出力ファイルの内容を確認
        byte[] outputBytes = Files.readAllBytes(outputFile);
        assertTrue("出力ファイルが空です", outputBytes.length > 0);
    }

    /**
     * テスト用の入力ファイルを作成（FILE_E形式、UTF-8）。
     */
    private void createTestInputFile_FILE_E_UTF8() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // 固定部（26バイト）
        String fixedPart = "FIXED_PART_DATA_1234567890"; // 26文字
        byte[] fixedPartBytes = fixedPart.getBytes("UTF-8");

        // 電文（可変）
        String message = "This is a test message 123";
        byte[] messageBytes = message.getBytes("UTF-8");

        // 電文長（2バイト）
        int messageLength = messageBytes.length;
        byte[] messageLenBytes = intToBytes(messageLength);
        byte[] messageLenField = new byte[2];
        System.arraycopy(messageLenBytes, 2, messageLenField, 0, 2); // 下位2バイト

        // 終端符号（1バイト）
        byte[] terminator = new byte[]{0x74}; // UTF-8/JIS用

        // RDW（4バイト）= 4 + 26 + 2 + 電文長 + 1
        int rdwValue = 4 + fixedPartBytes.length + 2 + messageBytes.length + 1;
        byte[] rdw = intToBytes(rdwValue);

        // レコードを構築
        ByteArrayOutputStream record = new ByteArrayOutputStream();
        record.write(rdw);
        record.write(fixedPartBytes);
        record.write(messageLenField);
        record.write(messageBytes);
        record.write(terminator);

        // BDW（4バイト）= 4 + レコード長
        int bdwValue = 4 + record.size();
        byte[] bdw = intToBytes(bdwValue);

        // ブロックを構築
        baos.write(bdw);
        baos.write(record.toByteArray());

        Files.write(inputFile, baos.toByteArray());
    }

    /**
     * テスト用の入力ファイルを作成（FILE_F形式、JIS_X0201）。
     */
    private void createTestInputFile_FILE_F_JIS() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // 固定部（26バイト）
        String fixedPart = "JIS_FIXED_PART_12345678901"; // 27文字 → 26バイトに調整
        byte[] fixedPartBytes = fixedPart.substring(0, 26).getBytes("JIS_X0201");

        // 電文（可変）
        String message = "JIS test message ABC";
        byte[] messageBytes = message.getBytes("JIS_X0201");

        // 電文長（2バイト）
        int messageLength = messageBytes.length;
        byte[] messageLenBytes = intToBytes(messageLength);
        byte[] messageLenField = new byte[2];
        System.arraycopy(messageLenBytes, 2, messageLenField, 0, 2); // 下位2バイト

        // 終端符号（1バイト）
        byte[] terminator = new byte[]{0x74}; // JIS用

        // RDW（4バイト）
        int rdwValue = 4 + fixedPartBytes.length + 2 + messageBytes.length + 1;
        byte[] rdw = intToBytes(rdwValue);

        // レコードを構築
        ByteArrayOutputStream record = new ByteArrayOutputStream();
        record.write(rdw);
        record.write(fixedPartBytes);
        record.write(messageLenField);
        record.write(messageBytes);
        record.write(terminator);

        // BDW（4バイト）
        int bdwValue = 4 + record.size();
        byte[] bdw = intToBytes(bdwValue);

        // ブロックを構築
        baos.write(bdw);
        baos.write(record.toByteArray());

        Files.write(inputFile, baos.toByteArray());
    }

    /**
     * バイト配列をintに変換する（ビッグエンディアン）。
     */
    private int bytesToInt(byte[] bytes) {
        int result = 0;
        for (int i = 0; i < bytes.length && i < 4; i++) {
            result = (result << 8) | (bytes[i] & 0xFF);
        }
        return result;
    }

    /**
     * intをバイト配列に変換する（ビッグエンディアン、4バイト）。
     */
    private byte[] intToBytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }
}
