package com.lenovo.agentconsole.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.agentconsole.domain.entity.McpServer;
import com.lenovo.agentconsole.domain.entity.Skill;
import com.lenovo.agentconsole.mapper.McpServerMapper;
import com.lenovo.agentconsole.mapper.SkillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssetQueryService {

    private final SkillMapper skillMapper;
    private final McpServerMapper mcpServerMapper;

    public Page<Skill> skills(int page, int size, String platform, String scope, String keyword) {
        LambdaQueryWrapper<Skill> q = new LambdaQueryWrapper<>();
        if (platform != null && !platform.isBlank()) {
            q.eq(Skill::getPlatform, platform);
        }
        if (scope != null && !scope.isBlank()) {
            q.eq(Skill::getScope, scope);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(Skill::getName, keyword).or().like(Skill::getDescription, keyword));
        }
        q.orderByAsc(Skill::getPlatform).orderByAsc(Skill::getName);
        return skillMapper.selectPage(new Page<>(page, size), q);
    }

    public Page<McpServer> mcp(int page, int size, String platform) {
        LambdaQueryWrapper<McpServer> q = new LambdaQueryWrapper<>();
        if (platform != null && !platform.isBlank()) {
            q.eq(McpServer::getPlatform, platform);
        }
        q.orderByAsc(McpServer::getPlatform).orderByAsc(McpServer::getName);
        return mcpServerMapper.selectPage(new Page<>(page, size), q);
    }
}
