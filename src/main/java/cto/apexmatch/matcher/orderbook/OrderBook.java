package cto.apexmatch.matcher.orderbook;

import cto.apexmatch.matcher.model.Order;
import cto.apexmatch.matcher.model.OrderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

/**
 * In-memory order book for a single symbol
 * Buy orders: TreeMap sorted by price DESC (highest first)
 * Sell orders: TreeMap sorted by price ASC (lowest first)
 */
public class OrderBook {
    private static final Logger logger = LoggerFactory.getLogger(OrderBook.class);

    private final String symbol;
    
    // Buy orders: Price -> Queue of orders (FIFO at same price for time priority)
    private final TreeMap<BigDecimal, Queue<Order>> buyOrders;
    
    // Sell orders: Price -> Queue of orders (FIFO at same price for time priority)
    private final TreeMap<BigDecimal, Queue<Order>> sellOrders;

    public OrderBook(String symbol) {
        this.symbol = symbol;
        // Buy orders: descending order (highest price first)
        this.buyOrders = new TreeMap<>(Collections.reverseOrder());
        // Sell orders: ascending order (lowest price first)
        this.sellOrders = new TreeMap<>();
    }

    /**
     * Add buy order to order book
     */
    public void addBuyOrder(Order order) {
        if (order.getType() != OrderType.BUY) {
            throw new IllegalArgumentException("Order must be BUY type");
        }
        buyOrders.computeIfAbsent(order.getPrice(), k -> new LinkedList<>()).add(order);
        logger.debug("Added BUY order {} at price {} for symbol {}", order.getId(), order.getPrice(), symbol);
    }

    /**
     * Add sell order to order book
     */
    public void addSellOrder(Order order) {
        if (order.getType() != OrderType.SELL) {
            throw new IllegalArgumentException("Order must be SELL type");
        }
        sellOrders.computeIfAbsent(order.getPrice(), k -> new LinkedList<>()).add(order);
        logger.debug("Added SELL order {} at price {} for symbol {}", order.getId(), order.getPrice(), symbol);
    }

    /**
     * Get best (highest) buy price
     */
    public BigDecimal getBestBuyPrice() {
        if (buyOrders.isEmpty()) {
            return null;
        }
        return buyOrders.firstKey();
    }

    /**
     * Get best (lowest) sell price
     */
    public BigDecimal getBestSellPrice() {
        if (sellOrders.isEmpty()) {
            return null;
        }
        return sellOrders.firstKey();
    }

    /**
     * Get best buy order (next to be matched)
     */
    public Order getBestBuyOrder() {
        if (buyOrders.isEmpty()) {
            return null;
        }
        Queue<Order> orders = buyOrders.firstEntry().getValue();
        return orders.isEmpty() ? null : orders.peek();
    }

    /**
     * Get best sell order (next to be matched)
     */
    public Order getBestSellOrder() {
        if (sellOrders.isEmpty()) {
            return null;
        }
        Queue<Order> orders = sellOrders.firstEntry().getValue();
        return orders.isEmpty() ? null : orders.peek();
    }

    /**
     * Remove order from order book
     */
    public void removeOrder(Order order) {
        if (order.getType() == OrderType.BUY) {
            Queue<Order> orders = buyOrders.get(order.getPrice());
            if (orders != null) {
                orders.remove(order);
                if (orders.isEmpty()) {
                    buyOrders.remove(order.getPrice());
                }
                logger.debug("Removed BUY order {} from order book", order.getId());
            }
        } else {
            Queue<Order> orders = sellOrders.get(order.getPrice());
            if (orders != null) {
                orders.remove(order);
                if (orders.isEmpty()) {
                    sellOrders.remove(order.getPrice());
                }
                logger.debug("Removed SELL order {} from order book", order.getId());
            }
        }
    }

    /**
     * Get all buy orders at a specific price level
     */
    public List<Order> getBuyOrdersAtPrice(BigDecimal price) {
        Queue<Order> orders = buyOrders.get(price);
        return orders != null ? new ArrayList<>(orders) : new ArrayList<>();
    }

    /**
     * Get all sell orders at a specific price level
     */
    public List<Order> getSellOrdersAtPrice(BigDecimal price) {
        Queue<Order> orders = sellOrders.get(price);
        return orders != null ? new ArrayList<>(orders) : new ArrayList<>();
    }

    /**
     * Get order book snapshot (for display/websocket)
     */
    public OrderBookSnapshot getSnapshot() {
        List<OrderBookLevel> buyLevels = new ArrayList<>();
        for (Map.Entry<BigDecimal, Queue<Order>> entry : buyOrders.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                BigDecimal totalQty = BigDecimal.ZERO;
                for (Order order : entry.getValue()) {
                    totalQty = totalQty.add(order.getRemainingQuantity());
                }
                buyLevels.add(new OrderBookLevel(entry.getKey(), totalQty, entry.getValue().size()));
            }
        }

        List<OrderBookLevel> sellLevels = new ArrayList<>();
        for (Map.Entry<BigDecimal, Queue<Order>> entry : sellOrders.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                BigDecimal totalQty = BigDecimal.ZERO;
                for (Order order : entry.getValue()) {
                    totalQty = totalQty.add(order.getRemainingQuantity());
                }
                sellLevels.add(new OrderBookLevel(entry.getKey(), totalQty, entry.getValue().size()));
            }
        }

        return new OrderBookSnapshot(symbol, buyLevels, sellLevels);
    }

    /**
     * Check if book has any orders
     */
    public boolean isEmpty() {
        return buyOrders.isEmpty() && sellOrders.isEmpty();
    }

    /**
     * Get symbol
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Order book level (for snapshot)
     */
    public static class OrderBookLevel {
        public final BigDecimal price;
        public final BigDecimal quantity;
        public final int orderCount;

        public OrderBookLevel(BigDecimal price, BigDecimal quantity, int orderCount) {
            this.price = price;
            this.quantity = quantity;
            this.orderCount = orderCount;
        }
    }

    /**
     * Order book snapshot
     */
    public static class OrderBookSnapshot {
        public final String symbol;
        public final List<OrderBookLevel> buyOrders;
        public final List<OrderBookLevel> sellOrders;

        public OrderBookSnapshot(String symbol, List<OrderBookLevel> buyOrders, List<OrderBookLevel> sellOrders) {
            this.symbol = symbol;
            this.buyOrders = buyOrders;
            this.sellOrders = sellOrders;
        }
    }
}
