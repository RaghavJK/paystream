package com.rk.paystream.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling // drives OutboxRelay
public class PaymentServiceApplication {

    public static void main(String[] args) {
        // Run in UTC. The PostgreSQL driver sends the JVM time zone when it connects, and Windows
        // reports the legacy name "Asia/Calcutta", which newer PostgreSQL images reject.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
