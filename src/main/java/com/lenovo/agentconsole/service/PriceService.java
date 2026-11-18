package com.lenovo.agentconsole.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lenovo.agentconsole.domain.entity.ModelPrice;
import com.lenovo.agentconsole.mapper.ModelPriceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PriceService {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final ModelPriceMapper mapper;

    private volatile List<ModelPrice> cache;
    private volatile long cachedAt;

    public List<ModelPrice> list() {
        return mapper.selectList(new QueryWrapper<ModelPrice>().orderByDesc("effective_from"));
    }

    public void invalidate() {
        cache = null;
    }

    public Optional<BigDecimal> cost(String provider, String model, LocalDate date,
                                     long input, long cached, long cacheWrite, long output) {
        ModelPrice price = find(provider, model, date);
        if (price == null) {
            return Optional.empty();
        }
        BigDecimal cost = nz(price.getInputPrice()).multiply(BigDecimal.valueOf(input))
                .add(nz(price.getCachedPrice()).multiply(BigDecimal.valueOf(cached)))
                .add(nz(price.getCacheWritePrice()).multiply(BigDecimal.valueOf(cacheWrite)))
                .add(nz(price.getOutputPrice()).multiply(BigDecimal.valueOf(output)))
                .divide(MILLION, 8, RoundingMode.HALF_UP);
        return Optional.of(cost.setScale(6, RoundingMode.HALF_UP));
    }

    private ModelPrice find(String provider, String model, LocalDate date) {
        if (model == null || date == null) {
            return null;
        }
        ModelPrice fallback = null;
        for (ModelPrice p : prices()) {
            if (p.getModel() == null || !p.getModel().equalsIgnoreCase(model)) {
                continue;
            }
            if (p.getEffectiveFrom() != null && p.getEffectiveFrom().isAfter(date)) {
                continue;
            }
            if (p.getEffectiveTo() != null && p.getEffectiveTo().isBefore(date)) {
                continue;
            }
            if (provider != null && provider.equalsIgnoreCase(p.getProvider())) {
                return p;
            }
            if (fallback == null) {
                fallback = p;
            }
        }
        return fallback;
    }

    private List<ModelPrice> prices() {
        long now = System.currentTimeMillis();
        if (cache == null || now - cachedAt > 60_000) {
            cache = mapper.selectList(null);
            cachedAt = now;
        }
        return cache;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
