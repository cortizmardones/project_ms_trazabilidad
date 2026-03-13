package com.java.trazabilidad.controller;

import com.java.trazabilidad.dto.ProductRequest;
import com.java.trazabilidad.dto.ProductResponse;
import com.java.trazabilidad.service.CallExternalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final CallExternalService callExternalService;

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@RequestBody ProductRequest productRequest) {

        log.info("Request recibida createProduct() productName = {} , productPrice = {} , companyRut = {}", productRequest.productName(), productRequest.productPrice(), productRequest.companyRut());
        return callExternalService.callExternalService(productRequest);
    }

}
