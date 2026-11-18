package com.lenovo.agentconsole.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Scan scan = new Scan();
    private Paths paths = new Paths();

    @Data
    public static class Scan {
        private boolean enabled = true;
        private long intervalMs = 120000;
    }

    @Data
    public static class Paths {
        private String codexHome;
        private String traeHome;
        private String traeCnHome;
        private String claudeHome;
        private String traeUsageXlsx;
    }
}
