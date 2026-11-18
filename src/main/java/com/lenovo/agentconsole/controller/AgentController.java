package com.lenovo.agentconsole.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.agentconsole.common.ApiResult;
import com.lenovo.agentconsole.domain.entity.AgentInstance;
import com.lenovo.agentconsole.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping
    public ApiResult<Page<AgentInstance>> page(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String platform,
                                               @RequestParam(required = false) String project,
                                               @RequestParam(required = false) String keyword) {
        return ApiResult.ok(agentService.page(page, size, platform, project, keyword));
    }

    @GetMapping("/{id}")
    public ApiResult<AgentInstance> detail(@PathVariable Long id) {
        return ApiResult.ok(agentService.detail(id));
    }
}
