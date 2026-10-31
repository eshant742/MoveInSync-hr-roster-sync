package com.moveinsync.roster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the HR Roster Sync application.
 *
 * <p>Provides idempotent, out-of-order, self-healing synchronisation
 * between upstream HR systems and the MoveInSync transport roster.</p>
 */
@SpringBootApplication
@EnableScheduling
public class HrRosterSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrRosterSyncApplication.class, args);
    }
}
