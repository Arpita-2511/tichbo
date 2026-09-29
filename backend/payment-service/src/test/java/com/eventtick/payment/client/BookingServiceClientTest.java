package com.eventtick.payment.client;

import com.eventtick.payment.exception.BookingNotFoundException;
import com.eventtick.payment.exception.BookingServiceUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves {@link BookingServiceClient}'s own HTTP/JSON behavior against a
 * real (tiny, JDK-built-in {@link HttpServer}) stub — the same
 * "real infrastructure over a mock" style
 * {@code com.eventtick.gateway.StubUpstream} already uses in this project,
 * rather than a new mocking library. No real booking-service is started.
 */
class BookingServiceClientTest {

    private HttpServer server;
    private final AtomicReference<String> lastPath = new AtomicReference<>();

    private BookingServiceClient clientReturning(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new BookingServiceClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void getBooking_success_parsesTheBookingSummary() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BookingServiceClient client = clientReturning(200, """
                {"bookingId":"%s","userId":"%s","showId":"%s","status":"PENDING","totalAmount":1450.00,
                 "seats":[],"createdAt":null,"updatedAt":null}
                """.formatted(bookingId, userId, UUID.randomUUID()));

        BookingSummary summary = client.getBooking(bookingId);

        assertThat(summary.bookingId()).isEqualTo(bookingId);
        assertThat(summary.userId()).isEqualTo(userId);
        assertThat(summary.status()).isEqualTo("PENDING");
        assertThat(summary.totalAmount()).isEqualByComparingTo("1450.00");
        assertThat(summary.isPending()).isTrue();
        assertThat(lastPath.get()).isEqualTo("/internal/bookings/" + bookingId);
    }

    @Test
    void getBooking_404_throwsBookingNotFound() throws Exception {
        UUID bookingId = UUID.randomUUID();
        BookingServiceClient client = clientReturning(404, "{\"status\":404,\"error\":\"BOOKING_NOT_FOUND\",\"message\":\"nope\"}");

        assertThatThrownBy(() -> client.getBooking(bookingId)).isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void getBooking_500_throwsBookingServiceUnavailable() throws Exception {
        UUID bookingId = UUID.randomUUID();
        BookingServiceClient client = clientReturning(500, "{\"status\":500,\"error\":\"INTERNAL\",\"message\":\"boom\"}");

        assertThatThrownBy(() -> client.getBooking(bookingId)).isInstanceOf(BookingServiceUnavailableException.class);
    }

    @Test
    void getBooking_connectionRefused_throwsBookingServiceUnavailable() {
        // No server started at all for this client — port 1 is reserved/unroutable.
        BookingServiceClient client = new BookingServiceClient("http://127.0.0.1:1");

        assertThatThrownBy(() -> client.getBooking(UUID.randomUUID())).isInstanceOf(BookingServiceUnavailableException.class);
    }

    @Test
    void confirmBooking_serverError_isSwallowed_notThrown_andReturnsFalse() throws Exception {
        // Best-effort per docs/architecture.md §25.1 scenario G — a failure
        // here must never surface as an exception to the payment-creation
        // caller, since the payment itself is already durably SUCCESS.
        // Step 3: the false return is what PaymentService now uses to
        // decide whether reconciliation is needed (Payment.bookingSyncStatus).
        BookingServiceClient client = clientReturning(500, "{}");

        assertThat(client.confirmBooking(UUID.randomUUID())).isFalse();
    }

    @Test
    void confirmBooking_success_returnsTrue_andUsesTheInternalPath() throws Exception {
        BookingServiceClient client = clientReturning(200, "{}");
        UUID bookingId = UUID.randomUUID();

        assertThat(client.confirmBooking(bookingId)).isTrue();
        assertThat(lastPath.get()).isEqualTo("/internal/bookings/" + bookingId + "/confirm");
    }

    @Test
    void releaseBooking_success_returnsTrue_andUsesTheInternalPath() throws Exception {
        BookingServiceClient client = clientReturning(200, "{}");
        UUID bookingId = UUID.randomUUID();

        assertThat(client.releaseBooking(bookingId)).isTrue();
        assertThat(lastPath.get()).isEqualTo("/internal/bookings/" + bookingId + "/cancel");
    }

    @Test
    void releaseBooking_serverError_isSwallowed_notThrown_andReturnsFalse() throws Exception {
        BookingServiceClient client = clientReturning(500, "{}");

        assertThat(client.releaseBooking(UUID.randomUUID())).isFalse();
    }
}
