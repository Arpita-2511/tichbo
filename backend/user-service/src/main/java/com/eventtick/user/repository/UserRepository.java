package com.eventtick.user.repository;

import com.eventtick.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Every finder here loads {@code User.plan} in the same query
 * ({@code @EntityGraph}), because every caller maps the result to a
 * {@code UserResponse}, which reads the plan's name. {@code User.plan} is
 * lazy, and with {@code open-in-view: false} the Hibernate session is
 * closed once the {@code @Transactional} service method returns — so
 * without this, {@code UserResponse.from} in the controller throws
 * {@code LazyInitializationException} (this is what broke
 * {@code GET /api/users/me}).
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Used for both the registration duplicate-email pre-check and the
     * login lookup. Callers must pass an already-lowercased email — this
     * repository does no case normalization (see {@code User.email}'s
     * Javadoc: the database doesn't enforce casing, so the application
     * must).
     */
    @EntityGraph(attributePaths = "plan")
    Optional<User> findByEmail(String email);

    /** Redeclared only to attach the entity graph; behavior is otherwise the inherited {@code findById}. */
    @Override
    @EntityGraph(attributePaths = "plan")
    Optional<User> findById(UUID id);

    /**
     * For {@code GET /api/admin/users} (Phase 13.4). A distinct method, not
     * {@code @Override findAll(Pageable)}: the inherited
     * {@link JpaRepository#findAll(Pageable)} has no entity graph, so using
     * it directly here would hit the same {@code LazyInitializationException}
     * this class's own Javadoc already describes.
     */
    @EntityGraph(attributePaths = "plan")
    Page<User> findAllBy(Pageable pageable);
}
