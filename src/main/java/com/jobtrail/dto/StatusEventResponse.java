package com.jobtrail.dto;

import com.jobtrail.entity.ApplicationStatus;
import com.jobtrail.entity.StatusEvent;

import java.time.Instant;

public record StatusEventResponse(
        Long id,
        ApplicationStatus status,
        Instant occurredAt
) {

    public static StatusEventResponse from(StatusEvent event) {
        return new StatusEventResponse(event.getId(), event.getStatus(), event.getOccurredAt());
    }
}
