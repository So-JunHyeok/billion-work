package com.billion21.work;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BillionWorkApplication {
    public static void main(String[] args) {
        SpringApplication.run(BillionWorkApplication.class, args);
    }
}
