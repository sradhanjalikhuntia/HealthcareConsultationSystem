package com.apexcare.auth.repository;

import com.apexcare.auth.entity.Role;
import com.apexcare.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // -----------------------------------------------------------------
    // Lookups — case-insensitive (usernames/emails are stored lowercase)
    // -----------------------------------------------------------------

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByDoctorCode(String doctorCode);

    boolean existsByDoctorCode(String doctorCode);

    Optional<User> findByLinkedPatientId(UUID linkedPatientId);

    // -----------------------------------------------------------------
    // Role-based queries
    // -----------------------------------------------------------------

    Page<User> findByRole(Role role, Pageable pageable);

    List<User> findByRoleAndEnabledTrue(Role role);

    long countByRole(Role role);

    long countByRoleAndEnabledTrue(Role role);

    // -----------------------------------------------------------------
    // Search — admin user directory
    // -----------------------------------------------------------------

    @Query("""
            SELECT u FROM User u
            WHERE (:role IS NULL OR u.role = :role)
              AND (:enabled IS NULL OR u.enabled = :enabled)
              AND (
                    :q IS NULL
                    OR LOWER(u.username)  LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(u.doctorCode) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<User> search(@Param("q") String q,
                      @Param("role") Role role,
                      @Param("enabled") Boolean enabled,
                      Pageable pageable);
}
