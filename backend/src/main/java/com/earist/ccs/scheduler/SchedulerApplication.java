package com.earist.ccs.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SchedulerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SchedulerApplication.class, args);
        System.out.println("=====================================");
        System.out.println("  EARIST Scheduler Started!");
        System.out.println("  Running on: http://localhost:8080");
        System.out.println("=====================================");
    }
}