package com.lenovo.agentconsole.support;

import java.math.BigDecimal;
import java.util.Map;

public final class MapUtil {

    private MapUtil() {
    }

    public static Object get(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    public static long lng(Map<String, Object> map, String key) {
        Object v = get(map, key);
        return v == null ? 0L : ((Number) v).longValue();
    }

    public static BigDecimal dec(Map<String, Object> map, String key) {
        Object v = get(map, key);
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        return new BigDecimal(v.toString());
    }
}
