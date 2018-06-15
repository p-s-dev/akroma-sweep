package com.psdev.aka.sweep.service;

import com.psdev.aka.sweep.data.MasternodeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import static javax.management.timer.Timer.ONE_MINUTE;
import static javax.management.timer.Timer.ONE_SECOND;

@Slf4j
@Service
public class NodeStatusService {

    @Autowired
    CredentialsService credentialsService;

    @Autowired
    AkromaApiService akromaApiService;

    private ConcurrentHashMap<String, MasternodeStatus> nodeStatusMap = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = ONE_MINUTE, initialDelay=ONE_SECOND * 10)
    public void doStatusCheck() throws Exception {
        akromaApiService.getNodeStatus().forEach(status -> {
            if (credentialsService.containsAddress(status.getAddress())) {
                if (status.getSuccessiveConnectionCount() == 0) {
                    log.error("** NODE FAILING HEALTHCHECK **  account=" + status.getAddress());
                }

                MasternodeStatus before = nodeStatusMap.getOrDefault(status.getAddress(), new MasternodeStatus());
                if (before.getLastSeenAt() != null &&
                        status.getSuccessiveConnectionCount() != before.getSuccessiveConnectionCount()) {
                    Duration healthCheckDelay =
                            Duration.ofMillis(status.getLastSeenAt().getTime() - before.getLastSeenAt().getTime());
                    status.setLastCheckDuration(healthCheckDelay);
                    log.info("healthcheck: account=" + status.getAddress() + " duration=" + healthCheckDelay);
                    nodeStatusMap.put(status.getAddress(), status);
                    log.debug("status: " + status);
                }

            }
        }
        );
    }

}
