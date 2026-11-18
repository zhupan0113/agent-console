package com.lenovo.agentconsole.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lenovo.agentconsole.domain.entity.ModelUsage;
import com.lenovo.agentconsole.domain.entity.UsageImport;
import com.lenovo.agentconsole.mapper.ModelUsageMapper;
import com.lenovo.agentconsole.mapper.UsageImportMapper;
import com.lenovo.agentconsole.support.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraeXlsxImporter {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };

    private final UsageImportMapper importMapper;
    private final ModelUsageMapper usageMapper;
    private final SettingService settingService;

    public Map<String, Object> importFile(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        String fileHash = HashUtil.sha256(bytes);

        UsageImport previous = importMapper.selectOne(new LambdaQueryWrapper<UsageImport>()
                .eq(UsageImport::getFileHash, fileHash)
                .eq(UsageImport::getStatus, "OK")
                .last("LIMIT 1"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", file.getFileName().toString());
        result.put("fileHash", fileHash);
        if (previous != null) {
            result.put("skipped", true);
            result.put("message", "同一文件已导入过，未重复写入");
            result.put("rows", previous.getRowsCount());
            result.put("credits", previous.getCredits());
            return result;
        }

        BigDecimal rate = settingService.getDecimal("trae.credit.cny", BigDecimal.ZERO);
        int rows = 0;
        int inserted = 0;
        BigDecimal creditsSum = BigDecimal.ZERO;
        Map<String, Integer> occurrence = new HashMap<>();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("明细");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }
            DataFormatter formatter = new DataFormatter();
            int headerRow = -1;
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String first = formatter.formatCellValue(row.getCell(1)).trim();
                if ("时间".equals(first)) {
                    headerRow = i;
                    break;
                }
            }
            if (headerRow < 0) {
                throw new IllegalStateException("未找到表头（时间/模型名称/积分消耗）");
            }
            for (int i = headerRow + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                LocalDateTime occurredAt = readDate(row.getCell(1));
                String model = text(row.getCell(2));
                BigDecimal credits = decimal(row.getCell(3));
                if (occurredAt == null || model == null || credits == null) {
                    continue;
                }
                rows++;
                creditsSum = creditsSum.add(credits);
                String key = occurredAt + "|" + model + "|" + credits.toPlainString();
                int index = occurrence.merge(key, 1, Integer::sum);

                ModelUsage usage = new ModelUsage();
                usage.setPlatform("trae");
                usage.setExternalId("trae:" + HashUtil.sha256(key + "#" + index));
                usage.setModel(model);
                usage.setProvider("trae");
                usage.setProject(null);
                usage.setInputTokens(0L);
                usage.setCachedTokens(0L);
                usage.setCacheWriteTokens(0L);
                usage.setOutputTokens(0L);
                usage.setReasoningTokens(0L);
                usage.setTotalTokens(0L);
                usage.setCredits(credits);
                usage.setCostAmount(credits.multiply(rate).setScale(6, RoundingMode.HALF_UP));
                usage.setCurrency("CNY");
                usage.setSource("xlsx");
                usage.setConfidence("credits");
                usage.setPriceMissing(false);
                usage.setOccurredAt(occurredAt);
                usage.setCreatedAt(LocalDateTime.now());
                try {
                    usageMapper.insert(usage);
                    inserted++;
                } catch (DuplicateKeyException ignored) {
                    // idempotent
                }
            }
        }

        UsageImport record = new UsageImport();
        record.setPlatform("trae");
        record.setFileName(file.getFileName().toString());
        record.setFileHash(fileHash);
        record.setRowsCount(rows);
        record.setCredits(creditsSum);
        record.setStatus("OK");
        record.setMessage("inserted=" + inserted);
        record.setImportedAt(LocalDateTime.now());
        importMapper.insert(record);

        result.put("skipped", false);
        result.put("rows", rows);
        result.put("inserted", inserted);
        result.put("credits", creditsSum);
        result.put("creditRate", rate);
        return result;
    }

    private LocalDateTime readDate(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue();
            }
            return LocalDateTime.of(1899, 12, 30, 0, 0)
                    .plusSeconds((long) (cell.getNumericCellValue() * 86400));
        }
        String text = new DataFormatter().formatCellValue(cell).trim();
        if (text.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(text, format);
            } catch (Exception ignored) {
                // try next
            }
        }
        return null;
    }

    private String text(Cell cell) {
        if (cell == null) {
            return null;
        }
        String value = new DataFormatter().formatCellValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    private BigDecimal decimal(Cell cell) {
        if (cell == null) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            String value = new DataFormatter().formatCellValue(cell).trim().replace(",", "");
            return value.isEmpty() ? null : new BigDecimal(value);
        } catch (Exception e) {
            return null;
        }
    }
}
