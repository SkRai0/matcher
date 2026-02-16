package cto.apexmatch.matcher.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderBookResponse {
    private String symbol;
    private List<OrderBookEntry> buyOrders;
    private List<OrderBookEntry> sellOrders;

    @Data
    public static class OrderBookEntry {
        private BigDecimal price;
        private BigDecimal totalQuantity;
        private int orderCount;
    }
}