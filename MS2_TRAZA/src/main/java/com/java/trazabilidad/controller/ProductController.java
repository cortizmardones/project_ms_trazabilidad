package com.java.trazabilidad.controller;

import com.java.trazabilidad.dto.ProductRequest;
import com.java.trazabilidad.dto.ProductResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/products")
@Slf4j
public class ProductController {

    @PostMapping
    public ResponseEntity<ProductResponse> getProducts(@RequestBody ProductRequest productRequest) {

        log.info("Request recibida createProduct() productName = {} , productPrice = {} , companyRut = {}", productRequest.productName(), productRequest.productPrice(), productRequest.companyRut());

        return ResponseEntity.status(200).body(
                ProductResponse.builder().status("OK")
                        .message("Mensaje recibido exitosamente por el microservicio-B")
                        .build());
    }

}
