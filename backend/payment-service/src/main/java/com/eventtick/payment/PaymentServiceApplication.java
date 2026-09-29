package com.eventtick.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Phase 15 Step 3: enables PaymentLifecycleScheduler's two @Scheduled
// sweeps (expiration, reconciliation) — see docs/architecture.md §25.3.
@EnableScheduling
@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
