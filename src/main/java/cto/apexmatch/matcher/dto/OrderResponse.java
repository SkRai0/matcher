package cto.apexmatch.matcher.dto;

import cto.apexmatch.matcher.model.OrderKind;
import cto.apexmatch.matcher.model.OrderStatus;
import cto.apexmatch.matcher.model.OrderType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderResponse {
    private Long id;
    private Long userId;
    private String symbol;
    private OrderType type;
    private OrderKind orderKind;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal filledQuantity;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}