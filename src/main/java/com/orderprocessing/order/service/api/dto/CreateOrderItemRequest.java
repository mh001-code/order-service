package com.orderprocessing.order.service.api.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderItemRequest(
        @NotNull UUID productId,
        @NotBlank String productName,
        @Min(1) @Max(100) int quantity,
        @NotNull @DecimalMin("0.01") BigDecimal unitPrice
) {}
