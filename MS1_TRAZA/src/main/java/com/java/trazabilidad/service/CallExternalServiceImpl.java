package com.java.trazabilidad.service;

import com.java.trazabilidad.clients.MsBClient;
import com.java.trazabilidad.dto.ProductRequest;
import com.java.trazabilidad.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CallExternalServiceImpl implements CallExternalService {

    private final MsBClient  msBClient;

    @Override
    public ResponseEntity<ProductResponse> callExternalService(ProductRequest productRequest) {
        log.info("LLamando a MICROSERVICIO-B con el request : {}", productRequest);
        return msBClient.callExternalService(productRequest);
    }

}
