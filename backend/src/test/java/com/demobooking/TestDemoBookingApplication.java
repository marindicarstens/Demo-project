package com.demobooking;

import org.springframework.boot.SpringApplication;

public class TestDemoBookingApplication {

	public static void main(String[] args) {
		SpringApplication.from(DemoBookingApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
