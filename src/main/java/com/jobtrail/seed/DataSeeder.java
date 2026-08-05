package com.jobtrail.seed;

import com.jobtrail.entity.Application;
import com.jobtrail.entity.ApplicationStatus;
import com.jobtrail.entity.Company;
import com.jobtrail.entity.StatusEvent;
import com.jobtrail.entity.User;
import com.jobtrail.repository.ApplicationRepository;
import com.jobtrail.repository.CompanyRepository;
import com.jobtrail.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Seeds a demo account with sample data on an empty database so the API is
 * explorable immediately after startup. Skipped in tests.
 */
@Component
@Profile("!test")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final ApplicationRepository applicationRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      CompanyRepository companyRepository,
                      ApplicationRepository applicationRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.applicationRepository = applicationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }

        User demo = new User();
        demo.setEmail("demo@jobtrail.dev");
        demo.setPassword(passwordEncoder.encode("password123"));
        userRepository.save(demo);

        seedApplication(demo, "Acme", "Backend Engineer",
                List.of(ApplicationStatus.SAVED, ApplicationStatus.APPLIED, ApplicationStatus.PHONE_SCREEN));
        seedApplication(demo, "Initech", "Java Developer",
                List.of(ApplicationStatus.APPLIED, ApplicationStatus.OA));
        seedApplication(demo, "Globex", "Platform Engineer",
                List.of(ApplicationStatus.APPLIED, ApplicationStatus.REJECTED));

        log.info("Seeded demo user demo@jobtrail.dev with sample applications");
    }

    private void seedApplication(User user, String companyName, String position, List<ApplicationStatus> history) {
        Company company = companyRepository.findByNameIgnoreCase(companyName)
                .orElseGet(() -> {
                    Company created = new Company();
                    created.setName(companyName);
                    return companyRepository.save(created);
                });

        Application application = new Application();
        application.setUser(user);
        application.setCompany(company);
        application.setPosition(position);
        application.setStatus(history.get(history.size() - 1));

        Instant base = Instant.now().minus(Duration.ofDays(history.size()));
        for (int i = 0; i < history.size(); i++) {
            StatusEvent event = new StatusEvent();
            event.setApplication(application);
            event.setStatus(history.get(i));
            event.setOccurredAt(base.plus(Duration.ofDays(i)));
            application.getStatusEvents().add(event);
        }
        applicationRepository.save(application);
    }
}
