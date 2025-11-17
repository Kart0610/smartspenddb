package com.example.smartspendapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartspendappApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartspendappApplication.class, args);
	}

}
