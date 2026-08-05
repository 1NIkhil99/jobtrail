package com.jobtrail.service;

import com.jobtrail.dto.ApplicationResponse;
import com.jobtrail.dto.CreateApplicationRequest;
import com.jobtrail.dto.UpdateApplicationRequest;
import com.jobtrail.entity.Application;
import com.jobtrail.entity.ApplicationStatus;
import com.jobtrail.entity.Company;
import com.jobtrail.entity.StatusEvent;
import com.jobtrail.exception.ResourceNotFoundException;
import com.jobtrail.repository.ApplicationRepository;
import com.jobtrail.repository.CompanyRepository;
import com.jobtrail.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final ApplicationRepository applicationRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    public ApplicationService(ApplicationRepository applicationRepository,
                              CompanyRepository companyRepository,
                              UserRepository userRepository) {
        this.applicationRepository = applicationRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ApplicationResponse create(Long userId, CreateApplicationRequest request) {
        ApplicationStatus initialStatus = request.initialStatus();

        Application application = new Application();
        application.setUser(userRepository.getReferenceById(userId));
        application.setCompany(findOrCreateCompany(request.companyName()));
        application.setPosition(request.position());
        application.setJobUrl(request.jobUrl());
        application.setNotes(request.notes());
        application.setStatus(initialStatus);
        appendStatusEvent(application, initialStatus);

        Application saved = applicationRepository.save(application);
        log.info("Created application {} for user {}", saved.getId(), userId);
        return ApplicationResponse.withEvents(saved);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> list(Long userId, ApplicationStatus status, Pageable pageable) {
        Page<Application> page = status == null
                ? applicationRepository.findAllWithDetailsByUserId(userId, pageable)
                : applicationRepository.findAllWithDetailsByUserIdAndStatus(userId, status, pageable);
        return page.map(ApplicationResponse::of);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getById(Long userId, Long id) {
        return ApplicationResponse.withEvents(loadOwnedWithDetails(userId, id));
    }

    @Transactional
    public ApplicationResponse update(Long userId, Long id, UpdateApplicationRequest request) {
        Application application = loadOwnedWithDetails(userId, id);

        if (!application.getCompany().getName().equalsIgnoreCase(request.companyName().trim())) {
            application.setCompany(findOrCreateCompany(request.companyName()));
        }
        application.setPosition(request.position());
        application.setJobUrl(request.jobUrl());
        application.setNotes(request.notes());

        if (application.getStatus() != request.status()) {
            application.setStatus(request.status());
            appendStatusEvent(application, request.status());
            log.info("Application {} moved to {} for user {}", id, request.status(), userId);
        }
        return ApplicationResponse.withEvents(application);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Application application = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> notFound(id));
        applicationRepository.delete(application);
        log.info("Deleted application {} for user {}", id, userId);
    }

    private Application loadOwnedWithDetails(Long userId, Long id) {
        return applicationRepository.findByIdAndUserIdWithDetails(id, userId)
                .orElseThrow(() -> notFound(id));
    }

    private Company findOrCreateCompany(String name) {
        String normalized = name.trim();
        return companyRepository.findByNameIgnoreCase(normalized)
                .orElseGet(() -> {
                    Company company = new Company();
                    company.setName(normalized);
                    return companyRepository.save(company);
                });
    }

    private void appendStatusEvent(Application application, ApplicationStatus status) {
        StatusEvent event = new StatusEvent();
        event.setApplication(application);
        event.setStatus(status);
        event.setOccurredAt(Instant.now());
        application.getStatusEvents().add(event);
    }

    private ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Application %d not found".formatted(id));
    }
}
