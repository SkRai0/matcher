package cto.apexmatch.matcher.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TradeResponse {
    private Long id;
    private Long buyOrderId;
    private Long sellOrderId;
    private BigDecimal price;
    private BigDecimal quantity;
    private LocalDateTime timestamp;
}