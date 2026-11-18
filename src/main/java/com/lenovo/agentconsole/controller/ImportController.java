package com.lenovo.agentconsole.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lenovo.agentconsole.common.ApiResult;
import com.lenovo.agentconsole.config.AppProperties;
import com.lenovo.agentconsole.domain.entity.UsageImport;
import com.lenovo.agentconsole.mapper.UsageImportMapper;
import com.lenovo.agentconsole.service.SettingService;
import com.lenovo.agentconsole.service.TraeXlsxImporter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final TraeXlsxImporter importer;
    private final UsageImportMapper importMapper;
    private final SettingService settingService;
    private final AppProperties props;

    @PostMapping("/trae")
    public ApiResult<Map<String, Object>> importTrae(@RequestParam(required = false) MultipartFile file) throws Exception {
        Path path;
        if (file != null && !file.isEmpty()) {
            path = Files.createTempFile("trae-usage-", ".xlsx");
            file.transferTo(path);
        } else {
            path = Path.of(settingService.get("trae.usage.xlsx", props.getPaths().getTraeUsageXlsx()));
        }
        return ApiResult.ok(importer.importFile(path));
    }

    @GetMapping("/history")
    public ApiResult<List<UsageImport>> history() {
        return ApiResult.ok(importMapper.selectList(new QueryWrapper<UsageImport>()
                .orderByDesc("imported_at").last("LIMIT 20")));
    }
}
