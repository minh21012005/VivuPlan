package com.vivuplan.vivuplan_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VivuplanBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(VivuplanBeApplication.class, args);
	}

}
