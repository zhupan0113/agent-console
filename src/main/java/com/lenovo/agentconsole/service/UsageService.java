package com.lenovo.agentconsole.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.agentconsole.domain.entity.ModelUsage;
import com.lenovo.agentconsole.mapper.ModelUsageMapper;
import com.lenovo.agentconsole.support.MapUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UsageService {

    private final ModelUsageMapper usageMapper;

    public Map<String, Object> summary(LocalDate from, LocalDate to, String platform, String project) {
        QueryWrapper<ModelUsage> q = base(from, to, platform, project);
        q.select("COALESCE(SUM(cost_amount),0) AS cost",
                "COALESCE(SUM(credits),0) AS credits",
                "COALESCE(SUM(total_tokens),0) AS tokens",
                "COALESCE(SUM(input_tokens),0) AS inputTokens",
                "COALESCE(SUM(cached_tokens),0) AS cachedTokens",
                "COALESCE(SUM(output_tokens),0) AS outputTokens",
                "COUNT(*) AS calls");
        List<Map<String, Object>> rows = usageMapper.selectMaps(q);
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cost", MapUtil.dec(row, "cost").setScale(4, java.math.RoundingMode.HALF_UP));
        out.put("credits", MapUtil.dec(row, "credits").setScale(2, java.math.RoundingMode.HALF_UP));
        out.put("tokens", MapUtil.lng(row, "tokens"));
        out.put("inputTokens", MapUtil.lng(row, "inputTokens"));
        out.put("cachedTokens", MapUtil.lng(row, "cachedTokens"));
        out.put("outputTokens", MapUtil.lng(row, "outputTokens"));
        out.put("calls", MapUtil.lng(row, "calls"));
        return out;
    }

    public List<Map<String, Object>> daily(LocalDate from, LocalDate to, String platform, String project) {
        QueryWrapper<ModelUsage> q = base(from, to, platform, project);
        q.select("DATE_FORMAT(occurred_at,'%Y-%m-%d') AS d",
                "COALESCE(SUM(cost_amount),0) AS cost",
                "COALESCE(SUM(credits),0) AS credits",
                "COALESCE(SUM(total_tokens),0) AS tokens",
                "COUNT(*) AS calls");
        q.groupBy("DATE_FORMAT(occurred_at,'%Y-%m-%d')").orderByAsc("d");
        List<Map<String, Object>> raw = usageMapper.selectMaps(q);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : raw) {
            Map<String, Object> m = new LinkedHashMap<>();
            Object d = MapUtil.get(r, "d");
            m.put("date", d == null ? null : d.toString());
            m.put("cost", MapUtil.dec(r, "cost").setScale(4, java.math.RoundingMode.HALF_UP));
            m.put("credits", MapUtil.dec(r, "credits").setScale(2, java.math.RoundingMode.HALF_UP));
            m.put("tokens", MapUtil.lng(r, "tokens"));
            m.put("calls", MapUtil.lng(r, "calls"));
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> byModel(LocalDate from, LocalDate to, String platform, String project) {
        QueryWrapper<ModelUsage> q = base(from, to, platform, project);
        q.select("model", "platform",
                "COALESCE(SUM(cost_amount),0) AS cost",
                "COALESCE(SUM(credits),0) AS credits",
                "COALESCE(SUM(total_tokens),0) AS tokens",
                "COUNT(*) AS calls");
        q.groupBy("model", "platform").orderByDesc("cost").last("LIMIT 50");
        return usageMapper.selectMaps(q);
    }

    public List<Map<String, Object>> byPlatform(LocalDate from, LocalDate to) {
        QueryWrapper<ModelUsage> q = base(from, to, null, null);
        q.select("platform",
                "COALESCE(SUM(cost_amount),0) AS cost",
                "COALESCE(SUM(credits),0) AS credits",
                "COALESCE(SUM(total_tokens),0) AS tokens",
                "COUNT(*) AS calls");
        q.groupBy("platform").orderByDesc("cost");
        return usageMapper.selectMaps(q);
    }

    public List<Map<String, Object>> byProject(LocalDate from, LocalDate to) {
        QueryWrapper<ModelUsage> q = base(from, to, null, null);
        q.select("IFNULL(project,'未归属') AS project",
                "COALESCE(SUM(cost_amount),0) AS cost",
                "COALESCE(SUM(credits),0) AS credits",
                "COALESCE(SUM(total_tokens),0) AS tokens",
                "COUNT(*) AS calls");
        q.groupBy("IFNULL(project,'未归属')").orderByDesc("cost").last("LIMIT 50");
        return usageMapper.selectMaps(q);
    }

    public Page<ModelUsage> records(int page, int size, LocalDate from, LocalDate to,
                                    String platform, String model) {
        QueryWrapper<ModelUsage> q = base(from, to, platform, null);
        if (model != null && !model.isBlank()) {
            q.like("model", model);
        }
        q.orderByDesc("occurred_at");
        return usageMapper.selectPage(new Page<>(page, size), q);
    }

    private QueryWrapper<ModelUsage> base(LocalDate from, LocalDate to, String platform, String project) {
        QueryWrapper<ModelUsage> q = new QueryWrapper<>();
        q.ge("occurred_at", from.atStartOfDay());
        q.lt("occurred_at", to.plusDays(1).atStartOfDay());
        if (platform != null && !platform.isBlank()) {
            q.eq("platform", platform);
        }
        if (project != null && !project.isBlank()) {
            q.eq("project", project);
        }
        return q;
    }

    public BigDecimal totalCost() {
        List<Map<String, Object>> rows = usageMapper.selectMaps(new QueryWrapper<ModelUsage>()
                .select("COALESCE(SUM(cost_amount),0) AS cost"));
        return rows.isEmpty() ? BigDecimal.ZERO : MapUtil.dec(rows.get(0), "cost");
    }
}
