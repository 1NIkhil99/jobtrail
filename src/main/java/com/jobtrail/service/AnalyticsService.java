package com.jobtrail.service;

import com.jobtrail.dto.AnalyticsSummaryResponse;
import com.jobtrail.entity.ApplicationStatus;
import com.jobtrail.repository.ApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class AnalyticsService {

    private static final Set<ApplicationStatus> TERMINAL_STATUSES =
            EnumSet.of(ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN);

    private final ApplicationRepository applicationRepository;

    public AnalyticsService(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(Long userId) {
        Map<ApplicationStatus, Long> byStatus = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            byStatus.put(status, applicationRepository.countByUserIdAndStatus(userId, status));
        }

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long active = byStatus.entrySet().stream()
                .filter(entry -> !TERMINAL_STATUSES.contains(entry.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();

        return new AnalyticsSummaryResponse(total, active, byStatus);
    }
}
