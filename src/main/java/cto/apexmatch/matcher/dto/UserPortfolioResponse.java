package cto.apexmatch.matcher.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UserPortfolioResponse {
    private Long userId;
    private String email;
    private BigDecimal balance;
    private BigDecimal totalValue; // Could include portfolio value in future
}