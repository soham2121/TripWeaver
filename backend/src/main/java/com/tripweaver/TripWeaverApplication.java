package com.tripweaver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// The app has its own login (AuthController); without this exclusion Spring logs an unused generated password.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class TripWeaverApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripWeaverApplication.class, args);
    }
}
