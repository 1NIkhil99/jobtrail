package com.jobtrail.service;

import com.jobtrail.dto.ApplicationResponse;
import com.jobtrail.dto.CreateApplicationRequest;
import com.jobtrail.dto.UpdateApplicationRequest;
import com.jobtrail.entity.Application;
import com.jobtrail.entity.ApplicationStatus;
import com.jobtrail.entity.Company;
import com.jobtrail.entity.StatusEvent;
import com.jobtrail.entity.User;
import com.jobtrail.exception.ResourceNotFoundException;
import com.jobtrail.repository.ApplicationRepository;
import com.jobtrail.repository.CompanyRepository;
import com.jobtrail.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    private static final Long USER_ID = 42L;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ApplicationService applicationService;

    @Test
    void createReusesExistingCompany() {
        Company acme = company(1L, "Acme");
        when(companyRepository.findByNameIgnoreCase("Acme")).thenReturn(Optional.of(acme));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user(USER_ID));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        applicationService.create(USER_ID, createRequest("Acme", null));

        verify(companyRepository, never()).save(any());
        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(captor.capture());
        assertThat(captor.getValue().getCompany()).isSameAs(acme);
    }

    @Test
    void createPersistsNewCompanyAndTrimsName() {
        when(companyRepository.findByNameIgnoreCase("Initech")).thenReturn(Optional.empty());
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user(USER_ID));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        ApplicationResponse response = applicationService.create(USER_ID, createRequest("  Initech  ", null));

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Initech");
        assertThat(response.companyName()).isEqualTo("Initech");
    }

    @Test
    void createDefaultsToSavedAndRecordsInitialEvent() {
        when(companyRepository.findByNameIgnoreCase("Acme")).thenReturn(Optional.of(company(1L, "Acme")));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user(USER_ID));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        applicationService.create(USER_ID, createRequest("Acme", null));

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(captor.capture());
        Application saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ApplicationStatus.SAVED);
        assertThat(saved.getStatusEvents()).hasSize(1);
        assertThat(saved.getStatusEvents().get(0).getStatus()).isEqualTo(ApplicationStatus.SAVED);
    }

    @Test
    void createHonoursExplicitInitialStatus() {
        when(companyRepository.findByNameIgnoreCase("Acme")).thenReturn(Optional.of(company(1L, "Acme")));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user(USER_ID));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        applicationService.create(USER_ID, createRequest("Acme", ApplicationStatus.APPLIED));

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ApplicationStatus.APPLIED);
    }

    @Test
    void getByIdThrowsWhenNotOwnedOrMissing() {
        when(applicationRepository.findByIdAndUserIdWithDetails(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.getById(USER_ID, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateAppendsEventWhenStatusChanges() {
        Application application = application(5L, ApplicationStatus.APPLIED, company(1L, "Acme"));
        when(applicationRepository.findByIdAndUserIdWithDetails(5L, USER_ID)).thenReturn(Optional.of(application));

        ApplicationResponse response = applicationService.update(
                USER_ID, 5L, updateRequest("Acme", ApplicationStatus.INTERVIEW));

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.INTERVIEW);
        assertThat(application.getStatusEvents()).hasSize(2);
        assertThat(response.statusEvents()).hasSize(2);
    }

    @Test
    void updateDoesNotAppendEventWhenStatusUnchanged() {
        Application application = application(5L, ApplicationStatus.APPLIED, company(1L, "Acme"));
        when(applicationRepository.findByIdAndUserIdWithDetails(5L, USER_ID)).thenReturn(Optional.of(application));

        applicationService.update(USER_ID, 5L, updateRequest("Acme", ApplicationStatus.APPLIED));

        assertThat(application.getStatusEvents()).hasSize(1);
    }

    @Test
    void updateSwitchesCompanyWhenNameChanges() {
        Application application = application(5L, ApplicationStatus.APPLIED, company(1L, "Acme"));
        Company initech = company(2L, "Initech");
        when(applicationRepository.findByIdAndUserIdWithDetails(5L, USER_ID)).thenReturn(Optional.of(application));
        when(companyRepository.findByNameIgnoreCase("Initech")).thenReturn(Optional.of(initech));

        applicationService.update(USER_ID, 5L, updateRequest("Initech", ApplicationStatus.APPLIED));

        assertThat(application.getCompany()).isSameAs(initech);
    }

    @Test
    void listDelegatesToFilteredQueryWhenStatusGiven() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Application> empty = new PageImpl<>(List.of());
        when(applicationRepository.findAllWithDetailsByUserIdAndStatus(USER_ID, ApplicationStatus.OFFER, pageable))
                .thenReturn(empty);

        applicationService.list(USER_ID, ApplicationStatus.OFFER, pageable);

        verify(applicationRepository).findAllWithDetailsByUserIdAndStatus(USER_ID, ApplicationStatus.OFFER, pageable);
        verify(applicationRepository, never()).findAllWithDetailsByUserId(any(), any());
    }

    @Test
    void deleteThrowsWhenNotOwnedOrMissing() {
        when(applicationRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.delete(USER_ID, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(applicationRepository, never()).delete(any());
    }

    private static CreateApplicationRequest createRequest(String companyName, ApplicationStatus status) {
        return new CreateApplicationRequest(companyName, "Backend Engineer", null, null, status);
    }

    private static UpdateApplicationRequest updateRequest(String companyName, ApplicationStatus status) {
        return new UpdateApplicationRequest(companyName, "Backend Engineer", null, null, status);
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static Company company(Long id, String name) {
        Company company = new Company();
        company.setId(id);
        company.setName(name);
        return company;
    }

    private static Application application(Long id, ApplicationStatus status, Company company) {
        Application application = new Application();
        application.setId(id);
        application.setCompany(company);
        application.setPosition("Backend Engineer");
        application.setStatus(status);
        application.setCreatedAt(Instant.now());
        application.setUpdatedAt(Instant.now());
        StatusEvent event = new StatusEvent();
        event.setApplication(application);
        event.setStatus(status);
        event.setOccurredAt(Instant.now().minusSeconds(60));
        application.getStatusEvents().add(event);
        return application;
    }
}
