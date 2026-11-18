package com.lenovo.agentconsole.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lenovo.agentconsole.common.ApiResult;
import com.lenovo.agentconsole.domain.entity.ScanRun;
import com.lenovo.agentconsole.mapper.ScanRunMapper;
import com.lenovo.agentconsole.service.ScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scan")
@RequiredArgsConstructor
public class ScanController {

    private final ScanService scanService;
    private final ScanRunMapper scanRunMapper;

    @PostMapping
    public ApiResult<Map<String, Object>> scan(@RequestParam(defaultValue = "manual") String trigger) {
        return ApiResult.ok(scanService.scan(trigger));
    }

    @GetMapping("/runs")
    public ApiResult<List<ScanRun>> runs() {
        return ApiResult.ok(scanRunMapper.selectList(new QueryWrapper<ScanRun>()
                .orderByDesc("started_at").last("LIMIT 20")));
    }
}
