package com.lenovo.agentconsole.controller;

import com.lenovo.agentconsole.common.ApiResult;
import com.lenovo.agentconsole.service.SettingService;
import com.lenovo.agentconsole.service.CostRecalcService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService settingService;
    private final CostRecalcService costRecalcService;

    @GetMapping
    public ApiResult<Map<String, String>> all() {
        return ApiResult.ok(settingService.all());
    }

    @PutMapping
    public ApiResult<Map<String, String>> update(@RequestBody Map<String, String> values) {
        values.forEach(settingService::put);
        if (values.containsKey("trae.credit.cny")) {
            try {
                costRecalcService.recalcTrae(new BigDecimal(values.get("trae.credit.cny").trim()));
            } catch (Exception ignored) {
                // invalid number keeps previous cost
            }
        }
        return ApiResult.ok(settingService.all());
    }
}
