package com.yh.authsvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication
public class AuthSvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthSvcApplication.class, args);
    }
}
