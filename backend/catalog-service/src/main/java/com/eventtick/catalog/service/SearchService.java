package com.eventtick.catalog.service;

import com.eventtick.catalog.entity.Content;
import com.eventtick.catalog.entity.ContentType;
import com.eventtick.catalog.entity.ShowStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SearchService {

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public Page<Content> search(String q, ContentType category, LocalDate date,
                                String city, UUID venueId, Pageable pageable) {

        StringBuilder where = new StringBuilder("WHERE s.status = :scheduled");
        Map<String, Object> params = new HashMap<>();
        params.put("scheduled", ShowStatus.SCHEDULED);

        if (q != null && !q.isBlank()) {
            where.append(" AND (LOWER(c.title) LIKE :q"
                    + " OR LOWER(c.description) LIKE :q"
                    + " OR LOWER(c.genre) LIKE :q"
                    + " OR LOWER(c.language) LIKE :q)");
            params.put("q", "%" + q.strip().toLowerCase() + "%");
        }

        if (category != null) {
            where.append(" AND c.type = :category");
            params.put("category", category);
        }

        if (date != null) {
            Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            where.append(" AND s.startTime >= :dayStart AND s.startTime < :dayEnd");
            params.put("dayStart", dayStart);
            params.put("dayEnd", dayEnd);
        }

        if (city != null && !city.isBlank()) {
            where.append(" AND LOWER(v.city) = LOWER(:city)");
            params.put("city", city.strip());
        }

        if (venueId != null) {
            where.append(" AND v.id = :venueId");
            params.put("venueId", venueId);
        }

        String fromClause = " FROM Show s JOIN s.content c JOIN s.venue v ";

        String dataJpql = "SELECT DISTINCT c" + fromClause + where + " ORDER BY c.title ASC, c.id ASC";
        String countJpql = "SELECT COUNT(DISTINCT c.id)" + fromClause + where;

        TypedQuery<Content> dataQuery = em.createQuery(dataJpql, Content.class);
        TypedQuery<Long> countQuery = em.createQuery(countJpql, Long.class);

        params.forEach((k, v) -> {
            dataQuery.setParameter(k, v);
            countQuery.setParameter(k, v);
        });

        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        List<Content> results = dataQuery.getResultList();
        long total = countQuery.getSingleResult();

        return new PageImpl<>(results, pageable, total);
    }
}
