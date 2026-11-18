package com.lenovo.agentconsole.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lenovo.agentconsole.domain.entity.McpServer;
import com.lenovo.agentconsole.domain.entity.Skill;
import com.lenovo.agentconsole.mapper.McpServerMapper;
import com.lenovo.agentconsole.mapper.SkillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssetStore {

    private final SkillMapper skillMapper;
    private final McpServerMapper mcpServerMapper;

    public void upsertSkill(Skill skill) {
        Skill existing = skillMapper.selectOne(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getPlatform, skill.getPlatform())
                .eq(Skill::getPathHash, skill.getPathHash()));
        if (existing == null) {
            skillMapper.insert(skill);
        } else {
            skill.setId(existing.getId());
            skillMapper.updateById(skill);
        }
    }

    public void upsertMcp(McpServer server) {
        McpServer existing = mcpServerMapper.selectOne(new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getPlatform, server.getPlatform())
                .eq(McpServer::getPathHash, server.getPathHash()));
        if (existing == null) {
            mcpServerMapper.insert(server);
        } else {
            server.setId(existing.getId());
            mcpServerMapper.updateById(server);
        }
    }
}
