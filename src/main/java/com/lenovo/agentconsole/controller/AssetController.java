package com.lenovo.agentconsole.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.agentconsole.common.ApiResult;
import com.lenovo.agentconsole.domain.entity.McpServer;
import com.lenovo.agentconsole.domain.entity.Skill;
import com.lenovo.agentconsole.service.AssetQueryService;
import com.lenovo.agentconsole.service.AssetContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AssetController {

    private final AssetQueryService assetQueryService;
    private final AssetContentService assetContentService;

    @GetMapping("/skills")
    public ApiResult<Page<Skill>> skills(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(required = false) String platform,
                                         @RequestParam(required = false) String scope,
                                         @RequestParam(required = false) String keyword) {
        return ApiResult.ok(assetQueryService.skills(page, size, platform, scope, keyword));
    }

    @GetMapping("/mcp")
    public ApiResult<Page<McpServer>> mcp(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String platform) {
        return ApiResult.ok(assetQueryService.mcp(page, size, platform));
    }

    @GetMapping("/assets/list")
    public ApiResult<List<Map<String, Object>>> list(@RequestParam String dir) {
        return ApiResult.ok(assetContentService.list(dir));
    }

    @GetMapping("/assets/content")
    public ApiResult<Map<String, Object>> content(@RequestParam String path) {
        return ApiResult.ok(assetContentService.read(path));
    }
}
