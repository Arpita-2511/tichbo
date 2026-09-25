package com.eventtick.catalog.service;

import com.eventtick.catalog.entity.Content;
import com.eventtick.catalog.entity.Show;
import com.eventtick.catalog.entity.ShowStatus;
import com.eventtick.catalog.entity.Venue;
import com.eventtick.catalog.exception.CatalogEntityNotFoundException;
import com.eventtick.catalog.repository.ContentRepository;
import com.eventtick.catalog.repository.ShowRepository;
import com.eventtick.catalog.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 13.6.2: {@link ShowService#cancel(UUID)} directly — a plain Mockito
 * unit test (not a {@code @WebMvcTest}, where the service is mocked and so
 * cannot prove anything about its own internals), specifically to verify
 * the requirement that {@code content}/{@code venue}/{@code startTime}/
 * {@code endTime} are left untouched and only {@code status} changes,
 * regardless of the show's current status (the intentionally permissive
 * behavior for this phase — see {@link ShowService#cancel}'s Javadoc).
 */
@ExtendWith(MockitoExtension.class)
class ShowServiceCancelTest {

    @Mock
    private ShowRepository showRepository;
    @Mock
    private ContentRepository contentRepository;
    @Mock
    private VenueRepository venueRepository;

    private ShowService showService;

    private Content content;
    private Venue venue;
    private Instant startTime;
    private Instant endTime;

    @BeforeEach
    void setUp() {
        showService = new ShowService(showRepository, contentRepository, venueRepository);

        content = new Content();
        ReflectionTestUtils.setField(content, "id", UUID.randomUUID());
        venue = new Venue();
        ReflectionTestUtils.setField(venue, "id", UUID.randomUUID());
        startTime = Instant.parse("2026-10-01T18:00:00Z");
        endTime = Instant.parse("2026-10-01T20:30:00Z");
    }

    private Show showWith(ShowStatus status) {
        Show show = new Show();
        ReflectionTestUtils.setField(show, "id", UUID.randomUUID());
        show.setContent(content);
        show.setVenue(venue);
        show.setStartTime(startTime);
        show.setEndTime(endTime);
        show.setStatus(status);
        return show;
    }

    @ParameterizedTest
    @EnumSource(ShowStatus.class)
    void cancel_setsOnlyStatus_leavingContentVenueAndTimesUntouched_regardlessOfCurrentStatus(ShowStatus currentStatus) {
        // Covers A (SCHEDULED), B (COMPLETED), and C (already CANCELLED) —
        // the intentionally permissive behavior: every current status
        // transitions to CANCELLED, with no 409 rule.
        Show show = showWith(currentStatus);
        when(showRepository.findById(show.getId())).thenReturn(Optional.of(show));
        when(showRepository.save(any(Show.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Show result = showService.cancel(show.getId());

        assertThat(result.getStatus()).isEqualTo(ShowStatus.CANCELLED);
        assertThat(result.getContent()).isSameAs(content);
        assertThat(result.getVenue()).isSameAs(venue);
        assertThat(result.getStartTime()).isEqualTo(startTime);
        assertThat(result.getEndTime()).isEqualTo(endTime);

        // The exact same Show instance is what gets saved — cancel() never
        // builds a new Show or touches any field beyond status.
        ArgumentCaptor<Show> saved = ArgumentCaptor.forClass(Show.class);
        verify(showRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(show);
        assertThat(saved.getValue().getContent()).isSameAs(content);
        assertThat(saved.getValue().getVenue()).isSameAs(venue);
        assertThat(saved.getValue().getStartTime()).isEqualTo(startTime);
        assertThat(saved.getValue().getEndTime()).isEqualTo(endTime);

        // cancel() never resolves Content/Venue by id — it only touches the
        // Show already loaded, confirming it does not (even accidentally)
        // re-validate or replace the associations the way update() does.
        verify(contentRepository, org.mockito.Mockito.never()).findById(any());
        verify(venueRepository, org.mockito.Mockito.never()).findById(any());
    }

    // ---- D: unknown id ----

    @Test
    void cancel_unknownId_throwsCatalogEntityNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(showRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> showService.cancel(unknownId))
                .isInstanceOf(CatalogEntityNotFoundException.class)
                .hasMessageContaining(unknownId.toString());

        verify(showRepository, org.mockito.Mockito.never()).save(any());
    }
}
