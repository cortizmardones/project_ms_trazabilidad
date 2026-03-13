package com.java.trazabilidad.service;

import com.java.trazabilidad.dto.ProductRequest;
import com.java.trazabilidad.dto.ProductResponse;
import org.springframework.http.ResponseEntity;

public interface CallExternalService {

    ResponseEntity<ProductResponse> callExternalService(ProductRequest productRequest);
}
