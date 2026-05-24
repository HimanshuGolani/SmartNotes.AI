	package com.smartnotes_ai.smartnotes_ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SmartnotesAiApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartnotesAiApplication.class, args);
	}

}
