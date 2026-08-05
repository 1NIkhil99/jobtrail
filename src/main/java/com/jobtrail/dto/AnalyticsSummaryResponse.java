package com.jobtrail.dto;

import com.jobtrail.entity.ApplicationStatus;

import java.util.Map;

public record AnalyticsSummaryResponse(
        long total,
        long active,
        Map<ApplicationStatus, Long> byStatus
) {
}
