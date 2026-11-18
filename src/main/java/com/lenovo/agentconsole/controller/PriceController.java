package com.lenovo.agentconsole.controller;

import com.lenovo.agentconsole.common.ApiResult;
import com.lenovo.agentconsole.domain.entity.ModelPrice;
import com.lenovo.agentconsole.mapper.ModelPriceMapper;
import com.lenovo.agentconsole.service.CostRecalcService;
import com.lenovo.agentconsole.service.PriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/prices")
@RequiredArgsConstructor
public class PriceController {

    private final ModelPriceMapper priceMapper;
    private final PriceService priceService;
    private final CostRecalcService costRecalcService;

    @GetMapping
    public ApiResult<List<ModelPrice>> list() {
        return ApiResult.ok(priceService.list());
    }

    @PostMapping
    public ApiResult<ModelPrice> save(@RequestBody ModelPrice price) {
        if (price.getEffectiveFrom() == null) {
            price.setEffectiveFrom(LocalDate.now());
        }
        if (price.getCurrency() == null) {
            price.setCurrency("CNY");
        }
        if (price.getInputPrice() == null) {
            price.setInputPrice(BigDecimal.ZERO);
        }
        if (price.getCachedPrice() == null) {
            price.setCachedPrice(BigDecimal.ZERO);
        }
        if (price.getCacheWritePrice() == null) {
            price.setCacheWritePrice(BigDecimal.ZERO);
        }
        if (price.getOutputPrice() == null) {
            price.setOutputPrice(BigDecimal.ZERO);
        }
        if (price.getId() == null) {
            priceMapper.insert(price);
        } else {
            priceMapper.updateById(price);
        }
        priceService.invalidate();
        costRecalcService.recalcCodex();
        return ApiResult.ok(price);
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        priceMapper.deleteById(id);
        priceService.invalidate();
        costRecalcService.recalcCodex();
        return ApiResult.ok();
    }
}
