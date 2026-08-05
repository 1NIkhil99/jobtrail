package com.jobtrail.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jobtrail.entity.Application;
import com.jobtrail.entity.ApplicationStatus;
import com.jobtrail.entity.StatusEvent;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record ApplicationResponse(
        Long id,
        String companyName,
        String position,
        ApplicationStatus status,
        String jobUrl,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<StatusEventResponse> statusEvents
) {

    /**
     * List projection. Leaves {@code statusEvents} out so paginated queries
     * never need to fetch the events collection.
     */
    public static ApplicationResponse of(Application application) {
        return build(application, null);
    }

    /**
     * Detail projection including the full status history, oldest first.
     */
    public static ApplicationResponse withEvents(Application application) {
        List<StatusEventResponse> events = application.getStatusEvents().stream()
                .sorted(Comparator.comparing(StatusEvent::getOccurredAt))
                .map(StatusEventResponse::from)
                .toList();
        return build(application, events);
    }

    private static ApplicationResponse build(Application application, List<StatusEventResponse> events) {
        return new ApplicationResponse(
                application.getId(),
                application.getCompany().getName(),
                application.getPosition(),
                application.getStatus(),
                application.getJobUrl(),
                application.getNotes(),
                application.getCreatedAt(),
                application.getUpdatedAt(),
                events
        );
    }
}
