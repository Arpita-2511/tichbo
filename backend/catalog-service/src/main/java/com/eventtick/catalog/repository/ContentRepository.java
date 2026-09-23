package com.eventtick.catalog.repository;

import com.eventtick.catalog.entity.Content;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Content} (the {@code content}
 * table). No custom query methods yet — nothing else has a foreign key
 * pointing at {@code content} that would require a reverse lookup here
 * (that need lives on {@link ShowRepository}, via {@code content_id}).
 */
public interface ContentRepository extends JpaRepository<Content, UUID> {
}
