package com.eventtick.catalog.service;

import com.eventtick.catalog.entity.Content;
import com.eventtick.catalog.entity.ContentType;
import com.eventtick.catalog.entity.Show;
import com.eventtick.catalog.entity.ShowStatus;
import com.eventtick.catalog.entity.Venue;
import com.eventtick.catalog.repository.ContentRepository;
import com.eventtick.catalog.repository.ShowRepository;
import com.eventtick.catalog.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Sql(statements = {
        "ALTER TABLE content ALTER COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP",
        "ALTER TABLE content ALTER COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP",
        "ALTER TABLE venues ALTER COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP",
        "ALTER TABLE venues ALTER COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP",
        "ALTER TABLE shows ALTER COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP",
        "ALTER TABLE shows ALTER COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
})
class SearchServiceIntegrationTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private ShowRepository showRepository;

    private Content movieInception;
    private Content concertColdplay;
    private Content sportsCricket;
    private Venue pvr;
    private Venue stadium;

    @BeforeEach
    void seed() {
        showRepository.deleteAll();
        contentRepository.deleteAll();
        venueRepository.deleteAll();

        movieInception = saveContent(ContentType.MOVIE, "Inception", "A mind-bending thriller", "English", "Sci-Fi");
        concertColdplay = saveContent(ContentType.CONCERT, "Coldplay Live", "World tour concert", "English", "Rock");
        sportsCricket = saveContent(ContentType.SPORTS_MATCH, "IPL 2026 Final", "Cricket championship", "Hindi", "Cricket");

        pvr = saveVenue("PVR Cinemas", "Andheri West", "Mumbai");
        stadium = saveVenue("Wankhede Stadium", "Churchgate", "Mumbai");
        Venue delhiVenue = saveVenue("Select City Walk", "Saket", "Delhi");

        Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant nextWeek = Instant.now().plus(7, ChronoUnit.DAYS);

        saveShow(movieInception, pvr, tomorrow, tomorrow.plus(3, ChronoUnit.HOURS), ShowStatus.SCHEDULED);
        saveShow(concertColdplay, stadium, nextWeek, nextWeek.plus(4, ChronoUnit.HOURS), ShowStatus.SCHEDULED);
        saveShow(sportsCricket, stadium, tomorrow, tomorrow.plus(5, ChronoUnit.HOURS), ShowStatus.SCHEDULED);
        saveShow(movieInception, delhiVenue, nextWeek, nextWeek.plus(3, ChronoUnit.HOURS), ShowStatus.SCHEDULED);

        // A cancelled show — should never appear in search results
        saveShow(movieInception, pvr, nextWeek.plus(14, ChronoUnit.DAYS), nextWeek.plus(14, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS), ShowStatus.CANCELLED);
    }

    @Test
    void search_noFilters_returnsAllContentWithScheduledShows() {
        Page<Content> results = searchService.search(null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(Content::getTitle)
                .containsExactlyInAnyOrder("Inception", "Coldplay Live", "IPL 2026 Final");
    }

    @Test
    void search_textQuery_matchesTitle() {
        Page<Content> results = searchService.search("inception", null, null, null, null, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(Content::getTitle)
                .containsExactly("Inception");
    }

    @Test
    void search_textQuery_matchesDescription() {
        Page<Content> results = searchService.search("mind-bending", null, null, null, null, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(Content::getTitle)
                .containsExactly("Inception");
    }

    @Test
    void search_textQuery_matchesGenre() {
        Page<Content> results = searchService.search("rock", null, null, null, null, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(Content::getTitle)
                .containsExactly("Coldplay Live");
    }

    @Test
    void search_textQuery_matchesLanguage() {
        Page<Content> results = searchService.search("hindi", null, null, null, null, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(Content::getTitle)
                .containsExactly("IPL 2026 Final");
    }

    @Test
    void search_textQuery_caseInsensitive() {
        Page<Content> results = searchService.search("INCEPTION", null, null, null, null, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(Content::getTitle)
                .containsExactly("Inception");
    }

    @Test
    void search_textQuery_noMatch_returnsEmpty() {
        Page<Content> results = searchService.search("nonexistent", null, null, null, null, PageRequest.of(0, 20));

        assertThat(results.getContent()).isEmpty();
        assertThat(results.getTotalElements()).isZero();
    }

    @Test
    void search_categoryFilter_returnsOnlyMatchingType() {
        Page<Content> results = searchService.search(null, ContentType.MOVIE, null, null, null, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(Content::getTitle)
                .containsExactly("Inception");
    }

    @Test
    void search_categoryFilter_concert() {
        Page<Content> results = searchService.search(null, ContentType.CONCERT, null, null, null, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(Content::getTitle)
                .containsExactly("Coldplay Live");
    }

    @Test
    void search_locationFilter_returnsContentWithShowsInCity() {
        Page<Content> results = searchService.search(null, null, null, "Delhi", null, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(Content::getTitle)
                .containsExactly("Inception");
    }

    @Test
    void search_locationFilter_caseInsensitive() {
        Page<Content> results = searchService.search(null, null, null, "mumbai", null, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(Content::getTitle)
                .containsExactlyInAnyOrder("Inception", "Coldplay Live", "IPL 2026 Final");
    }

    @Test
    void search_venueFilter_returnsContentAtSpecificVenue() {
        Page<Content> results = searchService.search(null, null, null, null, stadium.getId(), PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(Content::getTitle)
                .containsExactlyInAnyOrder("Coldplay Live", "IPL 2026 Final");
    }

    @Test
    void search_dateFilter_returnsContentWithShowsOnDate() {
        LocalDate tomorrowDate = LocalDate.now().plusDays(1);
        Page<Content> results = searchService.search(null, null, tomorrowDate, null, null, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(Content::getTitle)
                .containsExactlyInAnyOrder("Inception", "IPL 2026 Final");
    }

    @Test
    void search_combinedFilters_textAndCategory() {
        Page<Content> results = searchService.search("live", ContentType.CONCERT, null, null, null, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(Content::getTitle)
                .containsExactly("Coldplay Live");
    }

    @Test
    void search_combinedFilters_categoryAndLocation() {
        Page<Content> results = searchService.search(null, ContentType.MOVIE, null, "Delhi", null, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(Content::getTitle)
                .containsExactly("Inception");
    }

    @Test
    void search_combinedFilters_noOverlap_returnsEmpty() {
        Page<Content> results = searchService.search(null, ContentType.CONCERT, null, "Delhi", null, PageRequest.of(0, 20));

        assertThat(results.getContent()).isEmpty();
    }

    @Test
    void search_cancelledShows_excluded() {
        showRepository.deleteAll();
        contentRepository.deleteAll();
        venueRepository.deleteAll();

        Content c = saveContent(ContentType.MOVIE, "CancelledOnly", "desc", "English", "Drama");
        Venue v = saveVenue("Test Venue", "Test Address", "TestCity");
        Instant future = Instant.now().plus(3, ChronoUnit.DAYS);
        saveShow(c, v, future, future.plus(2, ChronoUnit.HOURS), ShowStatus.CANCELLED);

        Page<Content> results = searchService.search(null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(results.getContent()).isEmpty();
    }

    @Test
    void search_pagination_firstPage() {
        Page<Content> results = searchService.search(null, null, null, null, null, PageRequest.of(0, 2));

        assertThat(results.getContent()).hasSize(2);
        assertThat(results.getTotalElements()).isEqualTo(3);
        assertThat(results.getTotalPages()).isEqualTo(2);
        assertThat(results.getNumber()).isZero();
    }

    @Test
    void search_pagination_secondPage() {
        Page<Content> results = searchService.search(null, null, null, null, null, PageRequest.of(1, 2));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getNumber()).isEqualTo(1);
    }

    @Test
    void search_pagination_beyondLastPage_returnsEmpty() {
        Page<Content> results = searchService.search(null, null, null, null, null, PageRequest.of(10, 20));

        assertThat(results.getContent()).isEmpty();
    }

    @Test
    void search_deterministicOrdering_sortedByTitle() {
        Page<Content> results = searchService.search(null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(results.getContent()).extracting(Content::getTitle)
                .containsExactly("Coldplay Live", "IPL 2026 Final", "Inception");
    }

    @Test
    void search_distinctResults_contentWithMultipleShows_appearsOnce() {
        // Inception already has 2 scheduled shows (Mumbai + Delhi); must appear only once
        Page<Content> results = searchService.search("inception", null, null, null, null, PageRequest.of(0, 20));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getTotalElements()).isEqualTo(1);
    }

    private Content saveContent(ContentType type, String title, String desc, String lang, String genre) {
        Content c = new Content();
        c.setType(type);
        c.setTitle(title);
        c.setDescription(desc);
        c.setLanguage(lang);
        c.setGenre(genre);
        return contentRepository.saveAndFlush(c);
    }

    private Venue saveVenue(String name, String address, String city) {
        Venue v = new Venue();
        v.setName(name);
        v.setAddress(address);
        v.setCity(city);
        return venueRepository.saveAndFlush(v);
    }

    private Show saveShow(Content content, Venue venue, Instant start, Instant end, ShowStatus status) {
        Show s = new Show();
        s.setContent(content);
        s.setVenue(venue);
        s.setStartTime(start);
        s.setEndTime(end);
        s.setStatus(status);
        return showRepository.saveAndFlush(s);
    }
}
