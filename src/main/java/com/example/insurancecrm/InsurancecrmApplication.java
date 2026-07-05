package com.example.insurancecrm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class InsurancecrmApplication {

	public static void main(String[] args) {
		// Every LocalDateTime.now() in this app (createdAt fields, communication log timestamps,
		// etc.) uses the JVM's default zone — must be set before anything else runs, since the
		// client operates in IST and containers otherwise default to UTC.
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
		SpringApplication.run(InsurancecrmApplication.class, args);
	}

}
