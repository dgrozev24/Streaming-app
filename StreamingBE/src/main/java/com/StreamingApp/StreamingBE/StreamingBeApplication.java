package com.StreamingApp.StreamingBE;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StreamingBeApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(StreamingBeApplication.class);
		application.addInitializers(new MediaEndpointInitializer());
		application.run(args);
	}

}
