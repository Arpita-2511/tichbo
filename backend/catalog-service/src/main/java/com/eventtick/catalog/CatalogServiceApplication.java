package com.eventtick.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Eventtick Catalog Service.
 *
 * <p>Owns bookable content, venues, physical seat layouts, and scheduled
 * shows (the {@code content}, {@code venues}, {@code seats}, and
 * {@code shows} tables — see {@code database/migrations/0004}–{@code 0007}).
 * {@code show_seats} (show-specific seat availability) is owned by
 * booking-service, not this service — see {@code docs/architecture.md} §17.
 * This is a bootable skeleton only; no controllers, services, or
 * repositories are implemented yet.
 */
@SpringBootApplication
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
