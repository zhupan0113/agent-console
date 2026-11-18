package com.lenovo.agentconsole.controller;

import com.lenovo.agentconsole.common.ApiResult;
import com.lenovo.agentconsole.service.OverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/overview")
@RequiredArgsConstructor
public class OverviewController {

    private final OverviewService overviewService;

    @GetMapping
    public ApiResult<Map<String, Object>> overview() {
        return ApiResult.ok(overviewService.overview());
    }
}
