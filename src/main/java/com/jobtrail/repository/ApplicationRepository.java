package com.jobtrail.repository;

import com.jobtrail.entity.Application;
import com.jobtrail.entity.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    Optional<Application> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndStatus(Long userId, ApplicationStatus status);

    /**
     * Paginated listing with the company join-fetched. Deliberately does NOT
     * fetch {@code statusEvents}: fetching a collection alongside pagination
     * forces Hibernate to paginate in memory.
     */
    @EntityGraph(attributePaths = "company")
    @Query("select a from Application a where a.user.id = :userId")
    Page<Application> findAllWithDetailsByUserId(@Param("userId") Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "company")
    @Query("select a from Application a where a.user.id = :userId and a.status = :status")
    Page<Application> findAllWithDetailsByUserIdAndStatus(@Param("userId") Long userId,
                                                          @Param("status") ApplicationStatus status,
                                                          Pageable pageable);

    /**
     * Single-row lookup with company and full status history in one query.
     */
    @EntityGraph(attributePaths = {"company", "statusEvents"})
    @Query("select a from Application a where a.id = :id and a.user.id = :userId")
    Optional<Application> findByIdAndUserIdWithDetails(@Param("id") Long id, @Param("userId") Long userId);
}
