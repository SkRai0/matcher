package cto.apexmatch.matcher.dto;

import cto.apexmatch.matcher.model.OrderKind;
import cto.apexmatch.matcher.model.OrderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderRequest {

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotNull(message = "Order type is required")
    private OrderType type; // BUY or SELL

    @NotNull(message = "Order kind is required")
    private OrderKind orderKind; // LIMIT or MARKET

    // Price is required for LIMIT orders, optional for MARKET orders
    @DecimalMin(value = "0.01", message = "Price must be greater than 0", inclusive = false)
    private BigDecimal price;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.01", message = "Quantity must be greater than 0", inclusive = false)
    private BigDecimal quantity;
}