package com.jobtrail.dto;

import com.jobtrail.entity.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CreateApplicationRequest(
        @NotBlank @Size(max = 120) String companyName,
        @NotBlank @Size(max = 150) String position,
        @URL @Size(max = 500) String jobUrl,
        @Size(max = 2000) String notes,
        ApplicationStatus status
) {

    /**
     * Applications start as {@link ApplicationStatus#SAVED} unless the client
     * says otherwise (e.g. importing something already applied to).
     */
    public ApplicationStatus initialStatus() {
        return status != null ? status : ApplicationStatus.SAVED;
    }
}
