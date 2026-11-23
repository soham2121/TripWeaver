package com.tripweaver.auth;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the demo travellers when {@code DEMO_PASSWORD} is set (D6). No password is stored in the repository; without
 * the variable nothing is seeded and people register instead.
 */
@Component
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);
    static final Map<String, String> DEMO = Map.of("asha", "Asha", "ravi", "Ravi", "meera", "Meera");

    private final UserService users;
    private final String password;

    public DemoUserSeeder(UserService users, @Value("${tripweaver.demo.password:}") String password) {
        this.users = users;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.isBlank()) return;
        DEMO.forEach((u, name) -> {
            if (users.find(u) == null) users.register(u, password, name);
        });
        log.info("Demo users ready: {}", DEMO.keySet());
    }
}
