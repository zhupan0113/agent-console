package com.lenovo.agentconsole.job;

import com.lenovo.agentconsole.config.AppProperties;
import com.lenovo.agentconsole.service.ScanService;
import com.lenovo.agentconsole.service.SettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledScanJob {

    private final ScanService scanService;
    private final AppProperties props;
    private final SettingService settingService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!props.getScan().isEnabled()) {
            return;
        }
        new Thread(() -> {
            try {
                Thread.sleep(1500);
                scanService.scan("startup");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("startup scan failed", e);
            }
        }, "startup-scan").start();
    }

    @Scheduled(fixedDelayString = "${app.scan.interval-ms:120000}", initialDelay = 30000)
    public void periodicScan() {
        if (!props.getScan().isEnabled() || !"true".equalsIgnoreCase(settingService.get("scan.auto", "true"))) {
            return;
        }
        if (scanService.isRunning()) {
            return;
        }
        try {
            scanService.scan("auto");
        } catch (Exception e) {
            log.warn("scheduled scan failed", e);
        }
    }
}
