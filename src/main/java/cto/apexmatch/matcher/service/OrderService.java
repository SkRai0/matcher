package cto.apexmatch.matcher.service;

import cto.apexmatch.matcher.dto.OrderRequest;
import cto.apexmatch.matcher.dto.OrderResponse;
import cto.apexmatch.matcher.model.Order;
import cto.apexmatch.matcher.model.OrderKind;
import cto.apexmatch.matcher.model.OrderStatus;
import cto.apexmatch.matcher.model.Trade;
import cto.apexmatch.matcher.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Order Service - High-level order management
 * Coordinates between DTOs, entities, and matching engine
 */
@Service
@Transactional
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final MatchingEngine matchingEngine;
    private final UserService userService;

    public OrderService(OrderRepository orderRepository, 
                       MatchingEngine matchingEngine,
                       UserService userService) {
        this.orderRepository = orderRepository;
        this.matchingEngine = matchingEngine;
        this.userService = userService;
    }

    /**
     * Place a new order
     * Validates funds, creates order, and executes matching
     */
    public OrderResponse placeOrder(Long userId, OrderRequest orderRequest) {
        logger.info("Placing {} {} order for user {}: {} units of {} at price {}",
                orderRequest.getType(), orderRequest.getOrderKind(), userId,
                orderRequest.getQuantity(), orderRequest.getSymbol(), orderRequest.getPrice());

        // Validate user exists
        userService.getUserById(userId);

        // Validate order request
        validateOrderRequest(orderRequest, userId);

        // Create order entity
        Order order = new Order();
        order.setUserId(userId);
        order.setSymbol(orderRequest.getSymbol().toUpperCase());
        order.setType(orderRequest.getType());
        order.setOrderKind(orderRequest.getOrderKind());
        order.setPrice(orderRequest.getPrice());
        order.setQuantity(orderRequest.getQuantity());
        order.setFilledQuantity(BigDecimal.ZERO);
        order.setStatus(OrderStatus.PENDING);

        // For BUY orders, validate user has sufficient balance for full order
        if (orderRequest.getType().name().equals("BUY")) {
            BigDecimal orderValue = orderRequest.getOrderKind() == OrderKind.LIMIT
                    ? orderRequest.getQuantity().multiply(orderRequest.getPrice())
                    : orderRequest.getQuantity(); // For market orders, rough estimate
            
            BigDecimal userBalance = userService.getBalance(userId);
            if (userBalance.compareTo(orderValue) < 0) {
                throw new IllegalArgumentException("Insufficient balance. Required: " + orderValue + ", Available: " + userBalance);
            }
        }

        // Save order to DB
        order = orderRepository.save(order);
        logger.debug("Order created in DB with ID: {}", order.getId());

        // Execute matching
        List<Trade> trades = matchingEngine.executeOrder(order);
        logger.info("Order matching completed. Executed {} trades", trades.size());

        // Reload order from DB to get updated state
        order = orderRepository.findById(order.getId()).orElse(order);

        return convertToResponse(order);
    }

    /**
     * Cancel an order
     */
    public OrderResponse cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        // Verify order belongs to user
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Order does not belong to user");
        }

        // Cancel order via matching engine
        matchingEngine.cancelOrder(order);

        // Reload to get updated status
        order = orderRepository.findById(orderId).orElse(order);

        return convertToResponse(order);
    }

    /**
     * Get user's order history
     */
    public List<OrderResponse> getUserOrders(Long userId) {
        // Get all user orders (open and closed)
        List<Order> orders = orderRepository.findByUserIdAndStatusIn(userId, 
                List.of(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED, OrderStatus.CANCELLED));
        
        return orders.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get order details
     */
    public OrderResponse getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));
        return convertToResponse(order);
    }

    /**
     * Get open orders for a symbol
     */
    public List<OrderResponse> getOpenOrdersForSymbol(String symbol) {
        List<Order> orders = orderRepository.findBySymbolAndStatusIn(symbol.toUpperCase(),
                List.of(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED));
        
        return orders.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Validate order request
     */
    private void validateOrderRequest(OrderRequest orderRequest, Long userId) {
        // Symbol validation
        if (orderRequest.getSymbol() == null || orderRequest.getSymbol().trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol is required");
        }

        // Type and kind validation
        if (orderRequest.getType() == null) {
            throw new IllegalArgumentException("Order type (BUY/SELL) is required");
        }
        if (orderRequest.getOrderKind() == null) {
            throw new IllegalArgumentException("Order kind (LIMIT/MARKET) is required");
        }

        // Quantity validation
        if (orderRequest.getQuantity() == null || orderRequest.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        // Price validation (required for LIMIT orders)
        if (orderRequest.getOrderKind() == OrderKind.LIMIT) {
            if (orderRequest.getPrice() == null || orderRequest.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Price is required for LIMIT orders and must be greater than 0");
            }
        }
    }

    /**
     * Convert Order entity to OrderResponse DTO
     */
    private OrderResponse convertToResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setUserId(order.getUserId());
        response.setSymbol(order.getSymbol());
        response.setType(order.getType());
        response.setOrderKind(order.getOrderKind());
        response.setPrice(order.getPrice());
        response.setQuantity(order.getQuantity());
        response.setFilledQuantity(order.getFilledQuantity());
        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        return response;
    }
}
