package com.jobtrail;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "JobTrail API",
        version = "v1",
        description = """
                Job application tracking API.

                Obtain a token from /api/v1/auth/login (the seeded demo account is
                demo@jobtrail.dev / password123), then click Authorize and paste it
                to call the protected endpoints."""))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class JobTrailApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobTrailApplication.class, args);
    }
}
