package com.amalitech.hilfe.config;

import com.amalitech.hilfe.services.AutoCloseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoCloseScheduler {

    private final AutoCloseService autoCloseService;

    @Scheduled(cron = "${auto-close.cron:0 0 * * * *}")
    public void runAutoClose() {
        log.debug("Auto-close scheduler triggered");
        autoCloseService.autoCloseResolvedIncidents();
    }
}
