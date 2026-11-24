package com.reviewlens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReviewlensApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReviewlensApplication.class, args);
	}

}
