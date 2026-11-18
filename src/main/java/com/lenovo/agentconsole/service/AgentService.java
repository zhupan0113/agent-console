package com.lenovo.agentconsole.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.agentconsole.domain.entity.AgentInstance;
import com.lenovo.agentconsole.mapper.AgentInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentInstanceMapper agentMapper;

    public Page<AgentInstance> page(int page, int size, String platform, String project, String keyword) {
        LambdaQueryWrapper<AgentInstance> q = new LambdaQueryWrapper<>();
        if (platform != null && !platform.isBlank()) {
            q.eq(AgentInstance::getPlatform, platform);
        }
        if (project != null && !project.isBlank()) {
            q.eq(AgentInstance::getProject, project);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(AgentInstance::getName, keyword).or().like(AgentInstance::getCwd, keyword));
        }
        q.orderByDesc(AgentInstance::getUpdatedAt);
        return agentMapper.selectPage(new Page<>(page, size), q);
    }

    public AgentInstance detail(Long id) {
        return agentMapper.selectById(id);
    }
}
