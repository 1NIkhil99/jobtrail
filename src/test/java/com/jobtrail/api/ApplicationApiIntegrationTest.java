package com.jobtrail.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrail.dto.CreateApplicationRequest;
import com.jobtrail.dto.UpdateApplicationRequest;
import com.jobtrail.entity.ApplicationStatus;
import com.jobtrail.entity.User;
import com.jobtrail.repository.ApplicationRepository;
import com.jobtrail.repository.CompanyRepository;
import com.jobtrail.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class ApplicationApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private CompanyRepository companyRepository;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        companyRepository.deleteAll();
        userRepository.deleteAll();
        alice = persistUser("alice@example.com");
        bob = persistUser("bob@example.com");
    }

    @Test
    void createReturns201WithLocationAndInitialStatusEvent() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .with(auth(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("Acme", ApplicationStatus.SAVED))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.companyName").value("Acme"))
                .andExpect(jsonPath("$.status").value("SAVED"))
                .andExpect(jsonPath("$.statusEvents.length()").value(1))
                .andExpect(jsonPath("$.statusEvents[0].status").value("SAVED"));
    }

    @Test
    void createRejectsBlankCompanyName() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .with(auth(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateApplicationRequest("  ", "Backend Engineer", null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.companyName").exists());
    }

    @Test
    void listIsScopedToAuthenticatedUserAndFiltersByStatus() throws Exception {
        createApplication(alice, "Acme", ApplicationStatus.APPLIED);
        createApplication(alice, "Initech", ApplicationStatus.OFFER);
        createApplication(bob, "Globex", ApplicationStatus.APPLIED);

        mockMvc.perform(get("/api/v1/applications").with(auth(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/applications").with(auth(alice)).param("status", "OFFER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("Initech"));
    }

    @Test
    void listOmitsStatusEventsAndSupportsSorting() throws Exception {
        createApplication(alice, "Acme", ApplicationStatus.APPLIED);
        createApplication(alice, "Initech", ApplicationStatus.APPLIED);

        mockMvc.perform(get("/api/v1/applications")
                        .with(auth(alice))
                        .param("sort", "position,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].statusEvents").doesNotExist());
    }

    @Test
    void getReturnsFullStatusHistory() throws Exception {
        long id = createApplication(alice, "Acme", ApplicationStatus.APPLIED);

        mockMvc.perform(get("/api/v1/applications/{id}", id).with(auth(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusEvents.length()").value(1));
    }

    @Test
    void getReturns404ForAnotherUsersApplication() throws Exception {
        long id = createApplication(alice, "Acme", ApplicationStatus.APPLIED);

        mockMvc.perform(get("/api/v1/applications/{id}", id).with(auth(bob)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStatusChangeAppendsStatusEvent() throws Exception {
        long id = createApplication(alice, "Acme", ApplicationStatus.APPLIED);

        UpdateApplicationRequest update =
                new UpdateApplicationRequest("Acme", "Backend Engineer", null, null, ApplicationStatus.INTERVIEW);

        mockMvc.perform(put("/api/v1/applications/{id}", id)
                        .with(auth(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEW"))
                .andExpect(jsonPath("$.statusEvents.length()").value(2))
                .andExpect(jsonPath("$.statusEvents[1].status").value("INTERVIEW"));
    }

    @Test
    void deleteReturns204AndSubsequentGet404() throws Exception {
        long id = createApplication(alice, "Acme", ApplicationStatus.APPLIED);

        mockMvc.perform(delete("/api/v1/applications/{id}", id).with(auth(alice)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/applications/{id}", id).with(auth(alice)))
                .andExpect(status().isNotFound());
    }

    @Test
    void analyticsSummaryCountsOnlyOwnApplications() throws Exception {
        createApplication(alice, "Acme", ApplicationStatus.APPLIED);
        createApplication(alice, "Initech", ApplicationStatus.REJECTED);
        createApplication(bob, "Globex", ApplicationStatus.APPLIED);

        mockMvc.perform(get("/api/v1/analytics/summary").with(auth(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.active").value(1))
                .andExpect(jsonPath("$.byStatus.APPLIED").value(1))
                .andExpect(jsonPath("$.byStatus.REJECTED").value(1));
    }

    @Test
    void requestsWithoutAuthenticationAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/applications"))
                .andExpect(status().isUnauthorized());
    }

    private long createApplication(User user, String companyName, ApplicationStatus status) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/applications")
                        .with(auth(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(companyName, status))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private static CreateApplicationRequest request(String companyName, ApplicationStatus status) {
        return new CreateApplicationRequest(companyName, "Backend Engineer", null, null, status);
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("irrelevant-for-these-tests");
        return userRepository.save(user);
    }

    private static RequestPostProcessor auth(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }
}
