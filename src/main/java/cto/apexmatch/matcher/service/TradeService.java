package cto.apexmatch.matcher.service;

import cto.apexmatch.matcher.dto.TradeResponse;
import cto.apexmatch.matcher.model.Trade;
import cto.apexmatch.matcher.repository.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Trade Service - Trade history and analysis
 */
@Service
@Transactional(readOnly = true)
public class TradeService {
    private static final Logger logger = LoggerFactory.getLogger(TradeService.class);

    private final TradeRepository tradeRepository;
    private final UserService userService;

    public TradeService(TradeRepository tradeRepository, UserService userService) {
        this.tradeRepository = tradeRepository;
        this.userService = userService;
    }

    /**
     * Get all trades for a user (as buyer or seller)
     */
    public List<TradeResponse> getUserTrades(Long userId) {
        logger.debug("Fetching all trades for user {}", userId);
        
        // Validate user exists
        userService.getUserById(userId);
        
        List<Trade> trades = tradeRepository.findByBuyOrder_UserIdOrSellOrder_UserIdOrderByTimestampDesc(userId, userId);
        
        return trades.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get trades where user was buyer
     */
    public List<TradeResponse> getUserBuyTrades(Long userId) {
        logger.debug("Fetching buy trades for user {}", userId);
        
        // Validate user exists
        userService.getUserById(userId);
        
        List<Trade> trades = tradeRepository.findByBuyOrder_UserIdOrderByTimestampDesc(userId);
        
        return trades.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get trades where user was seller
     */
    public List<TradeResponse> getUserSellTrades(Long userId) {
        logger.debug("Fetching sell trades for user {}", userId);
        
        // Validate user exists
        userService.getUserById(userId);
        
        List<Trade> trades = tradeRepository.findBySellOrder_UserIdOrderByTimestampDesc(userId);
        
        return trades.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get trades for a specific order
     */
    public List<TradeResponse> getOrderTrades(Long orderId) {
        logger.debug("Fetching trades for order {}", orderId);
        
        // Try finding as buy order first
        List<Trade> trades = tradeRepository.findByBuyOrderIdOrderByTimestampDesc(orderId);
        
        // If not found as buyer, try as seller
        if (trades.isEmpty()) {
            trades = tradeRepository.findBySellOrderIdOrderByTimestampDesc(orderId);
        }
        
        return trades.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Convert Trade entity to TradeResponse DTO
     */
    private TradeResponse convertToResponse(Trade trade) {
        TradeResponse response = new TradeResponse();
        response.setId(trade.getId());
        response.setBuyOrderId(trade.getBuyOrderId());
        response.setSellOrderId(trade.getSellOrderId());
        response.setPrice(trade.getPrice());
        response.setQuantity(trade.getQuantity());
        response.setTimestamp(trade.getTimestamp());
        return response;
    }
}
