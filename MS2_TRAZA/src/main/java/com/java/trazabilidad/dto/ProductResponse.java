package com.java.trazabilidad.dto;

import lombok.Builder;

@Builder
public record ProductResponse(

        String status,
        String message

) {
}
