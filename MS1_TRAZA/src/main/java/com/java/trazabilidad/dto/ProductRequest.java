package com.java.trazabilidad.dto;

import lombok.Builder;

@Builder
public record ProductRequest(

        String productName,
        Integer productPrice,
        String companyRut

) {
}