package com.java.trazabilidad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class TrazabilidadApplication {

	public static void main(String[] args) {
		SpringApplication.run(TrazabilidadApplication.class, args);
	}

}
