package com.lenovo.agentconsole.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lenovo.agentconsole.domain.entity.AgentInstance;
import com.lenovo.agentconsole.domain.entity.McpServer;
import com.lenovo.agentconsole.domain.entity.ModelUsage;
import com.lenovo.agentconsole.domain.entity.ScanRun;
import com.lenovo.agentconsole.domain.entity.Skill;
import com.lenovo.agentconsole.mapper.AgentInstanceMapper;
import com.lenovo.agentconsole.mapper.McpServerMapper;
import com.lenovo.agentconsole.mapper.ModelUsageMapper;
import com.lenovo.agentconsole.mapper.ScanRunMapper;
import com.lenovo.agentconsole.mapper.SkillMapper;
import com.lenovo.agentconsole.support.MapUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OverviewService {

    private final AgentInstanceMapper agentMapper;
    private final SkillMapper skillMapper;
    private final McpServerMapper mcpServerMapper;
    private final ModelUsageMapper usageMapper;
    private final ScanRunMapper scanRunMapper;
    private final UsageService usageService;

    public Map<String, Object> overview() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate yearAgo = today.minusDays(29);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("agents", agentMapper.selectCount(null));
        out.put("skills", skillMapper.selectCount(null));
        out.put("mcpServers", mcpServerMapper.selectCount(null));
        out.put("projects", distinctProjects());
        out.put("today", usageService.summary(today, today, null, null));
        out.put("month", usageService.summary(monthStart, today, null, null));
        out.put("allTime", usageService.summary(today.minusYears(5), today, null, null));
        out.put("trend", usageService.daily(yearAgo, today, null, null));
        out.put("byPlatform", usageService.byPlatform(monthStart, today));
        out.put("topModels", topModels(monthStart, today));
        out.put("recentScans", scanRunMapper.selectList(new QueryWrapper<ScanRun>()
                .orderByDesc("started_at").last("LIMIT 5")));
        out.put("priceMissing", usageMapper.selectCount(new QueryWrapper<ModelUsage>().eq("price_missing", true)));
        out.put("platforms", List.of(platformCard("codex"), platformCard("trae"), platformCard("claude")));
        return out;
    }

    private Map<String, Object> platformCard(String code) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("platform", code);
        card.put("agents", agentMapper.selectCount(new QueryWrapper<AgentInstance>().eq("platform", code)));
        card.put("skills", skillMapper.selectCount(new QueryWrapper<Skill>().eq("platform", code)));
        card.put("mcp", mcpServerMapper.selectCount(new QueryWrapper<McpServer>().eq("platform", code)));
        card.put("today", usageService.summary(today, today, code, null));
        card.put("month", usageService.summary(monthStart, today, code, null));
        card.put("trend", usageService.daily(today.minusDays(29), today, code, null));
        card.put("lastActive", lastActive(code));
        card.put("priceMissing", usageMapper.selectCount(new QueryWrapper<ModelUsage>()
                .eq("platform", code).eq("price_missing", true)));
        card.put("topModel", topModel(code));
        return card;
    }

    private Object lastActive(String code) {
        List<Map<String, Object>> rows = usageMapper.selectMaps(new QueryWrapper<ModelUsage>()
                .select("MAX(occurred_at) AS t").eq("platform", code));
        if (rows.isEmpty()) {
            return null;
        }
        Object value = MapUtil.get(rows.get(0), "t");
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return value instanceof java.time.LocalDateTime ? value : null;
    }

    private Map<String, Object> topModel(String code) {
        QueryWrapper<ModelUsage> q = new QueryWrapper<>();
        q.eq("platform", code).isNotNull("model");
        q.select("model", "COUNT(*) AS calls",
                "COALESCE(SUM(total_tokens),0) AS tokens",
                "COALESCE(SUM(credits),0) AS credits",
                "COALESCE(SUM(cost_amount),0) AS cost");
        q.groupBy("model").orderByDesc("calls").last("LIMIT 1");
        List<Map<String, Object>> rows = usageMapper.selectMaps(q);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long distinctProjects() {
        List<Map<String, Object>> rows = agentMapper.selectMaps(new QueryWrapper<AgentInstance>()
                .select("COUNT(DISTINCT project) AS c")
                .isNotNull("project"));
        return rows.isEmpty() ? 0 : MapUtil.lng(rows.get(0), "c");
    }

    private List<Map<String, Object>> topModels(LocalDate from, LocalDate to) {
        QueryWrapper<ModelUsage> q = new QueryWrapper<>();
        q.ge("occurred_at", from.atStartOfDay());
        q.lt("occurred_at", to.plusDays(1).atStartOfDay());
        q.select("model", "platform",
                "COALESCE(SUM(cost_amount),0) AS cost",
                "COALESCE(SUM(credits),0) AS credits",
                "COALESCE(SUM(total_tokens),0) AS tokens",
                "COUNT(*) AS calls");
        q.groupBy("model", "platform")
                .orderByDesc("COALESCE(SUM(cost_amount),0) + COALESCE(SUM(credits),0) * 0.01")
                .last("LIMIT 6");
        return usageMapper.selectMaps(q);
    }
}
