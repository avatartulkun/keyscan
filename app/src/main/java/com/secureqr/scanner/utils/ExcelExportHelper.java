package com.secureqr.scanner.utils;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.List;

public final class ExcelExportHelper {
    private ExcelExportHelper() {
    }

    public static byte[] workbookBytes(String sheetName, List<String> headers, List<List<String>> rows, String password) throws Exception {
        byte[] plain = plainWorkbook(sheetName, headers, rows);
        if (password == null || password.isEmpty()) return plain;
        return encryptWorkbook(plain, password);
    }

    private static byte[] plainWorkbook(String sheetName, List<String> headers, List<List<String>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet(sheetName);
            writeRow(sheet.createRow(0), headers);
            for (int i = 0; i < rows.size(); i++) {
                writeRow(sheet.createRow(i + 1), rows.get(i));
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void writeRow(Row row, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values.get(i) == null ? "" : values.get(i));
        }
    }

    private static byte[] encryptWorkbook(byte[] workbook, String password) throws Exception {
        try (POIFSFileSystem fs = new POIFSFileSystem();
             OPCPackage opc = OPCPackage.open(new ByteArrayInputStream(workbook));
             ByteArrayOutputStream encrypted = new ByteArrayOutputStream()) {
            EncryptionInfo info = new EncryptionInfo(EncryptionMode.agile);
            Encryptor encryptor = info.getEncryptor();
            encryptor.confirmPassword(password);
            try (OutputStream stream = encryptor.getDataStream(fs)) {
                opc.save(stream);
            }
            fs.writeFilesystem(encrypted);
            return encrypted.toByteArray();
        }
    }
}

