package cto.apexmatch.matcher.service;

import cto.apexmatch.matcher.model.Order;
import cto.apexmatch.matcher.model.OrderKind;
import cto.apexmatch.matcher.model.OrderStatus;
import cto.apexmatch.matcher.model.OrderType;
import cto.apexmatch.matcher.model.Trade;
import cto.apexmatch.matcher.orderbook.OrderBook;
import cto.apexmatch.matcher.orderbook.OrderBookManager;
import cto.apexmatch.matcher.repository.OrderRepository;
import cto.apexmatch.matcher.repository.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Core matching engine implementing price-time priority matching
 * - Buy orders: Matched with lowest-priced sell orders
 * - Sell orders: Matched with highest-priced buy orders
 * - Time priority: Orders at same price matched FIFO
 * 
 * Concurrency: ReentrantLock per symbol allows parallel matching across symbols
 */
@Service
@Transactional
public class MatchingEngine {
    private static final Logger logger = LoggerFactory.getLogger(MatchingEngine.class);

    private final OrderBookManager orderBookManager;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final UserService userService;

    public MatchingEngine(OrderBookManager orderBookManager,
                         OrderRepository orderRepository,
                         TradeRepository tradeRepository,
                         UserService userService) {
        this.orderBookManager = orderBookManager;
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.userService = userService;
    }

    /**
     * Main entry point: Execute order and attempt to match
     * Returns list of trades executed
     */
    public List<Trade> executeOrder(Order order) {
        String symbol = order.getSymbol();
        ReentrantReadWriteLock lock = orderBookManager.getLock(symbol);
        
        lock.writeLock().lock();
        try {
            logger.info("Executing {} {} order for {} units at {} for symbol {}",
                    order.getType(), order.getOrderKind(), order.getQuantity(), 
                    order.getPrice(), symbol);

            List<Trade> trades = new ArrayList<>();
            OrderBook orderBook = orderBookManager.getOrderBook(symbol);

            if (order.getType() == OrderType.BUY) {
                trades = matchBuyOrder(order, orderBook);
            } else {
                trades = matchSellOrder(order, orderBook);
            }

            // Add unmatched order to order book if not fully filled
            if (!order.isFullyFilled()) {
                if (order.getType() == OrderType.BUY) {
                    orderBook.addBuyOrder(order);
                } else {
                    orderBook.addSellOrder(order);
                }
                order.setStatus(order.getFilledQuantity().compareTo(BigDecimal.ZERO) > 0 
                    ? OrderStatus.PARTIALLY_FILLED 
                    : OrderStatus.PENDING);
            } else {
                order.setStatus(OrderStatus.FILLED);
            }

            orderRepository.save(order);
            logger.info("Order {} execution complete. Filled: {}/{}, Trades: {}", 
                    order.getId(), order.getFilledQuantity(), order.getQuantity(), trades.size());

            return trades;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Match a BUY order against sell orders
     * Algorithm:
     * 1. Get best (lowest) sell price
     * 2. While buy.price >= sell.price AND buy.quantity > 0:
     *    - Match quantities
     *    - Create trade
     *    - Update balances
     *    - Remove fully filled orders from book
     */
    private List<Trade> matchBuyOrder(Order buyOrder, OrderBook orderBook) {
        List<Trade> trades = new ArrayList<>();
        BigDecimal buyPrice = buyOrder.getOrderKind() == OrderKind.MARKET 
                ? null 
                : buyOrder.getPrice();

        while (buyOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            Order sellOrder = orderBook.getBestSellOrder();
            
            // No sell order available OR buy price doesn't match
            if (sellOrder == null) {
                break;
            }

            // For limit orders, check price match
            if (buyPrice != null && sellOrder.getPrice().compareTo(buyPrice) > 0) {
                break;
            }

            // Match this trade
            Trade trade = createTrade(buyOrder, sellOrder, orderBook, trades);
            if (trade != null) {
                trades.add(trade);
            }

            // If sell order is fully filled, remove from book
            if (sellOrder.isFullyFilled()) {
                orderBook.removeOrder(sellOrder);
                sellOrder.setStatus(OrderStatus.FILLED);
                orderRepository.save(sellOrder);
            } else {
                sellOrder.setStatus(OrderStatus.PARTIALLY_FILLED);
                orderRepository.save(sellOrder);
            }
        }

        return trades;
    }

    /**
     * Match a SELL order against buy orders
     * Algorithm:
     * 1. Get best (highest) buy price
     * 2. While sell.price <= buy.price AND sell.quantity > 0:
     *    - Match quantities
     *    - Create trade
     *    - Update balances
     *    - Remove fully filled orders from book
     */
    private List<Trade> matchSellOrder(Order sellOrder, OrderBook orderBook) {
        List<Trade> trades = new ArrayList<>();
        BigDecimal sellPrice = sellOrder.getOrderKind() == OrderKind.MARKET 
                ? null 
                : sellOrder.getPrice();

        while (sellOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            Order buyOrder = orderBook.getBestBuyOrder();
            
            // No buy order available OR sell price doesn't match
            if (buyOrder == null) {
                break;
            }

            // For limit orders, check price match
            if (sellPrice != null && buyOrder.getPrice().compareTo(sellPrice) < 0) {
                break;
            }

            // Match this trade
            Trade trade = createTrade(buyOrder, sellOrder, orderBook, trades);
            if (trade != null) {
                trades.add(trade);
            }

            // If buy order is fully filled, remove from book
            if (buyOrder.isFullyFilled()) {
                orderBook.removeOrder(buyOrder);
                buyOrder.setStatus(OrderStatus.FILLED);
                orderRepository.save(buyOrder);
            } else {
                buyOrder.setStatus(OrderStatus.PARTIALLY_FILLED);
                orderRepository.save(buyOrder);
            }
        }

        return trades;
    }

    /**
     * Create a trade between two orders
     * Execution price is the maker's price (the order already in the book)
     */
    private Trade createTrade(Order buyOrder, Order sellOrder, OrderBook orderBook, List<Trade> previousTrades) {
        BigDecimal buyRemaining = buyOrder.getRemainingQuantity();
        BigDecimal sellRemaining = sellOrder.getRemainingQuantity();
        
        // Match quantity is minimum of remaining quantities
        BigDecimal matchQty = buyRemaining.min(sellRemaining);
        
        // Execution price is the sell order's price (maker's price)
        BigDecimal executionPrice = sellOrder.getPrice();

        // Create trade
        Trade trade = new Trade();
        trade.setBuyOrderId(buyOrder.getId());
        trade.setBuyOrder(buyOrder);
        trade.setSellOrderId(sellOrder.getId());
        trade.setSellOrder(sellOrder);
        trade.setPrice(executionPrice);
        trade.setQuantity(matchQty);

        // Update filled quantities
        buyOrder.setFilledQuantity(buyOrder.getFilledQuantity().add(matchQty));
        sellOrder.setFilledQuantity(sellOrder.getFilledQuantity().add(matchQty));

        // Update user balances
        // Buyer pays: quantity * executionPrice
        BigDecimal buyerPays = matchQty.multiply(executionPrice);
        userService.updateBalance(buyOrder.getUserId(), buyerPays.negate());

        // Seller receives: quantity * executionPrice
        userService.updateBalance(sellOrder.getUserId(), buyerPays);

        // Persist trade
        tradeRepository.save(trade);

        logger.info("Trade executed: {} units at {} between buy order {} and sell order {}",
                matchQty, executionPrice, buyOrder.getId(), sellOrder.getId());

        return trade;
    }

    /**
     * Cancel an order
     */
    public void cancelOrder(Order order) {
        String symbol = order.getSymbol();
        ReentrantReadWriteLock lock = orderBookManager.getLock(symbol);
        
        lock.writeLock().lock();
        try {
            OrderBook orderBook = orderBookManager.getOrderBook(symbol);
            
            if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.PARTIALLY_FILLED) {
                orderBook.removeOrder(order);
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);
                
                logger.info("Order {} cancelled", order.getId());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get order book snapshot
     */
    public OrderBook.OrderBookSnapshot getOrderBookSnapshot(String symbol) {
        return orderBookManager.getSnapshot(symbol);
    }
}
