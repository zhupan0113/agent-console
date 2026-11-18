package com.lenovo.agentconsole.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lenovo.agentconsole.adapter.ClaudeAdapter;
import com.lenovo.agentconsole.adapter.CodexAdapter;
import com.lenovo.agentconsole.adapter.TraeAdapter;
import com.lenovo.agentconsole.domain.entity.AgentInstance;
import com.lenovo.agentconsole.domain.entity.ModelUsage;
import com.lenovo.agentconsole.domain.entity.ScanRun;
import com.lenovo.agentconsole.mapper.AgentInstanceMapper;
import com.lenovo.agentconsole.mapper.ModelUsageMapper;
import com.lenovo.agentconsole.mapper.ScanRunMapper;
import com.lenovo.agentconsole.support.MapUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanService {

    private final CodexAdapter codexAdapter;
    private final TraeAdapter traeAdapter;
    private final ClaudeAdapter claudeAdapter;
    private final ScanRunMapper scanRunMapper;
    private final AgentInstanceMapper agentMapper;
    private final ModelUsageMapper usageMapper;
    private final CostRecalcService costRecalcService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public boolean isRunning() {
        return running.get();
    }

    public Map<String, Object> scan(String trigger) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!running.compareAndSet(false, true)) {
            result.put("status", "RUNNING");
            result.put("message", "已有扫描任务在执行");
            return result;
        }
        ScanRun run = new ScanRun();
        run.setPlatform("all");
        run.setTriggerType(trigger);
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        run.setItemCount(0);
        scanRunMapper.insert(run);

        long started = System.currentTimeMillis();
        int items = 0;
        List<String> errors = new ArrayList<>();
        try {
            try {
                items += codexAdapter.scanAssets();
            } catch (Exception e) {
                log.warn("codex asset scan failed", e);
                errors.add("codex assets: " + e.getMessage());
            }
            try {
                items += codexAdapter.ingestUsage();
            } catch (Exception e) {
                log.warn("codex usage ingest failed", e);
                errors.add("codex usage: " + e.getMessage());
            }
            usageMapper.backfillCodexModel();
            costRecalcService.recalcCodexMissing();
            try {
                items += traeAdapter.scanAssets();
            } catch (Exception e) {
                log.warn("trae asset scan failed", e);
                errors.add("trae assets: " + e.getMessage());
            }
            try {
                items += claudeAdapter.scanAssets();
            } catch (Exception e) {
                log.warn("claude asset scan failed", e);
                errors.add("claude assets: " + e.getMessage());
            }
            refreshAgentTotals();
        } finally {
            run.setFinishedAt(LocalDateTime.now());
            run.setItemCount(items);
            run.setStatus(errors.isEmpty() ? "OK" : "PARTIAL");
            run.setMessage(errors.isEmpty()
                    ? "耗时 " + (System.currentTimeMillis() - started) + "ms"
                    : String.join("; ", errors));
            scanRunMapper.updateById(run);
            running.set(false);
        }

        result.put("status", run.getStatus());
        result.put("items", items);
        result.put("elapsedMs", System.currentTimeMillis() - started);
        result.put("errors", errors);
        return result;
    }

    private void refreshAgentTotals() {
        List<Map<String, Object>> rows = usageMapper.selectMaps(new QueryWrapper<ModelUsage>()
                .select("thread_id", "SUM(total_tokens) AS tokens", "SUM(cost_amount) AS cost")
                .eq("platform", "codex")
                .isNotNull("thread_id")
                .groupBy("thread_id"));
        for (Map<String, Object> row : rows) {
            Object threadId = MapUtil.get(row, "thread_id");
            if (threadId == null) {
                continue;
            }
            agentMapper.update(null, new LambdaUpdateWrapper<AgentInstance>()
                    .eq(AgentInstance::getPlatform, "codex")
                    .eq(AgentInstance::getExternalId, threadId.toString())
                    .set(AgentInstance::getTokensUsed, MapUtil.lng(row, "tokens"))
                    .set(AgentInstance::getCostAmount, MapUtil.dec(row, "cost").setScale(4, java.math.RoundingMode.HALF_UP)));
        }
        agentMapper.update(null, new LambdaUpdateWrapper<AgentInstance>()
                .eq(AgentInstance::getPlatform, "codex")
                .isNull(AgentInstance::getTokensUsed)
                .set(AgentInstance::getTokensUsed, 0L)
                .set(AgentInstance::getCostAmount, BigDecimal.ZERO));
    }
}
