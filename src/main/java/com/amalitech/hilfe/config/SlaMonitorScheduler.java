package com.amalitech.hilfe.config;

import com.amalitech.hilfe.services.SlaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlaMonitorScheduler {

    private final SlaService slaService;

    @Scheduled(cron = "${sla-monitor.cron:0 * * * * *}")
    public void scan() {
        log.debug("SLA monitor scheduler triggered");
        slaService.scanAndNotify();
    }
}
