package com.lenovo.agentconsole.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lenovo.agentconsole.domain.entity.ModelUsage;
import com.lenovo.agentconsole.mapper.ModelUsageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostRecalcService {

    private final ModelUsageMapper usageMapper;
    private final PriceService priceService;

    public int recalcCodex() {
        return recalc(usageMapper.selectList(new QueryWrapper<ModelUsage>().eq("platform", "codex")));
    }

    public int recalcCodexMissing() {
        return recalc(usageMapper.selectList(new QueryWrapper<ModelUsage>()
                .eq("platform", "codex").eq("price_missing", true)));
    }

    private int recalc(List<ModelUsage> rows) {
        int updated = 0;
        for (ModelUsage row : rows) {
            Optional<BigDecimal> cost = priceService.cost(row.getProvider(), row.getModel(),
                    row.getOccurredAt().toLocalDate(), nz(row.getInputTokens()), nz(row.getCachedTokens()),
                    nz(row.getCacheWriteTokens()), nz(row.getOutputTokens()));
            BigDecimal value = cost.orElse(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
            boolean missing = cost.isEmpty();
            BigDecimal old = row.getCostAmount() == null ? BigDecimal.ZERO : row.getCostAmount();
            boolean oldMissing = Boolean.TRUE.equals(row.getPriceMissing());
            if (missing != oldMissing || value.compareTo(old.setScale(4, RoundingMode.HALF_UP)) != 0) {
                usageMapper.update(null, new LambdaUpdateWrapper<ModelUsage>()
                        .eq(ModelUsage::getId, row.getId())
                        .set(ModelUsage::getCostAmount, value)
                        .set(ModelUsage::getPriceMissing, missing));
                updated++;
            }
        }
        log.info("codex cost recalculated, updated={}", updated);
        return updated;
    }

    public int recalcTrae(BigDecimal rate) {
        BigDecimal safe = rate == null ? BigDecimal.ZERO : rate;
        usageMapper.update(null, new UpdateWrapper<ModelUsage>()
                .eq("platform", "trae")
                .setSql("cost_amount = ROUND(credits * " + safe.toPlainString() + ", 6)"));
        return usageMapper.selectCount(new QueryWrapper<ModelUsage>().eq("platform", "trae")) > 0 ? 1 : 0;
    }

    private long nz(Long value) {
        return value == null ? 0L : value;
    }
}
