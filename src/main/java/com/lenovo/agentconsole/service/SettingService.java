package com.lenovo.agentconsole.service;

import com.lenovo.agentconsole.domain.entity.AppSetting;
import com.lenovo.agentconsole.mapper.AppSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingService {

    private final AppSettingMapper mapper;

    public String get(String key, String def) {
        AppSetting s = mapper.selectById(key);
        if (s == null || s.getV() == null || s.getV().isBlank()) {
            return def;
        }
        return s.getV();
    }

    public BigDecimal getDecimal(String key, BigDecimal def) {
        try {
            return new BigDecimal(get(key, def.toPlainString()).trim());
        } catch (Exception e) {
            return def;
        }
    }

    public void put(String key, String value) {
        AppSetting s = new AppSetting();
        s.setK(key);
        s.setV(value);
        s.setUpdatedAt(LocalDateTime.now());
        mapper.insertOrUpdate(s);
    }

    public Map<String, String> all() {
        Map<String, String> map = new LinkedHashMap<>();
        mapper.selectList(null).forEach(s -> map.put(s.getK(), s.getV()));
        return map;
    }
}
