package cto.apexmatch.matcher.orderbook;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages order books for multiple symbols
 * Thread-safe access using ConcurrentHashMap and per-symbol locks
 */
@Component
public class OrderBookManager {

    // Symbol -> OrderBook
    private final ConcurrentHashMap<String, OrderBook> books = new ConcurrentHashMap<>();
    
    // Symbol -> ReentrantLock for thread safety during matching
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

    /**
     * Get order book for symbol (create if not exists)
     */
    public OrderBook getOrderBook(String symbol) {
        return books.computeIfAbsent(symbol, s -> new OrderBook(symbol));
    }

    /**
     * Get read-write lock for symbol
     */
    public ReentrantReadWriteLock getLock(String symbol) {
        return locks.computeIfAbsent(symbol, s -> new ReentrantReadWriteLock());
    }

    /**
     * Get order book snapshot for symbol
     */
    public OrderBook.OrderBookSnapshot getSnapshot(String symbol) {
        OrderBook book = books.get(symbol);
        if (book == null) {
            return new OrderBook.OrderBookSnapshot(symbol, java.util.List.of(), java.util.List.of());
        }
        ReentrantReadWriteLock lock = getLock(symbol);
        lock.readLock().lock();
        try {
            return book.getSnapshot();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clear all order books (for testing)
     */
    public void clear() {
        books.clear();
        locks.clear();
    }
}
