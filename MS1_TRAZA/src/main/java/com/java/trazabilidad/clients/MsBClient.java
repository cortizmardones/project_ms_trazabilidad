package com.java.trazabilidad.clients;

import com.java.trazabilidad.config.FeignTraceConfig;
import com.java.trazabilidad.dto.ProductRequest;
import com.java.trazabilidad.dto.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ms-b-client", url = "http://localhost:8082", configuration = FeignTraceConfig.class)
public interface MsBClient {

    @PostMapping("/v1/products")
    ResponseEntity<ProductResponse> callExternalService(@RequestBody ProductRequest productRequest);

}