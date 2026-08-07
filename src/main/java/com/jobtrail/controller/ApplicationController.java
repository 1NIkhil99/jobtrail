package com.jobtrail.controller;

import com.jobtrail.dto.ApplicationResponse;
import com.jobtrail.dto.CreateApplicationRequest;
import com.jobtrail.dto.UpdateApplicationRequest;
import com.jobtrail.entity.ApplicationStatus;
import com.jobtrail.service.ApplicationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/applications")
@SecurityRequirement(name = "bearerAuth")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@AuthenticationPrincipal Long userId,
                                                      @Valid @RequestBody CreateApplicationRequest request) {
        ApplicationResponse response = applicationService.create(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public Page<ApplicationResponse> list(@AuthenticationPrincipal Long userId,
                                          @RequestParam(required = false) ApplicationStatus status,
                                          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                          Pageable pageable) {
        return applicationService.list(userId, status, pageable);
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return applicationService.getById(userId, id);
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@AuthenticationPrincipal Long userId,
                                      @PathVariable Long id,
                                      @Valid @RequestBody UpdateApplicationRequest request) {
        return applicationService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        applicationService.delete(userId, id);
    }
}
