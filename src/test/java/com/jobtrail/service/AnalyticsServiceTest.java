package com.jobtrail.service;

import com.jobtrail.dto.AnalyticsSummaryResponse;
import com.jobtrail.entity.ApplicationStatus;
import com.jobtrail.repository.ApplicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final Long USER_ID = 42L;

    @Mock
    private ApplicationRepository applicationRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void summaryAggregatesTotalsAndExcludesTerminalStatusesFromActive() {
        when(applicationRepository.countByUserIdAndStatus(eq(USER_ID), any())).thenReturn(0L);
        when(applicationRepository.countByUserIdAndStatus(USER_ID, ApplicationStatus.APPLIED)).thenReturn(3L);
        when(applicationRepository.countByUserIdAndStatus(USER_ID, ApplicationStatus.INTERVIEW)).thenReturn(1L);
        when(applicationRepository.countByUserIdAndStatus(USER_ID, ApplicationStatus.REJECTED)).thenReturn(2L);
        when(applicationRepository.countByUserIdAndStatus(USER_ID, ApplicationStatus.WITHDRAWN)).thenReturn(1L);

        AnalyticsSummaryResponse summary = analyticsService.summary(USER_ID);

        assertThat(summary.total()).isEqualTo(7);
        assertThat(summary.active()).isEqualTo(4);
        assertThat(summary.byStatus())
                .containsEntry(ApplicationStatus.APPLIED, 3L)
                .containsEntry(ApplicationStatus.SAVED, 0L)
                .hasSize(ApplicationStatus.values().length);
    }

    @Test
    void summaryIsAllZerosForUserWithNoApplications() {
        when(applicationRepository.countByUserIdAndStatus(eq(USER_ID), any())).thenReturn(0L);

        AnalyticsSummaryResponse summary = analyticsService.summary(USER_ID);

        assertThat(summary.total()).isZero();
        assertThat(summary.active()).isZero();
    }
}
