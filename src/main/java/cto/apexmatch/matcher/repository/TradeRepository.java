package cto.apexmatch.matcher.repository;

import cto.apexmatch.matcher.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    // Find trades where user was buyer
    List<Trade> findByBuyOrder_UserIdOrderByTimestampDesc(Long userId);

    // Find trades where user was seller
    List<Trade> findBySellOrder_UserIdOrderByTimestampDesc(Long userId);

    // Find all trades for a user (as buyer or seller)
    List<Trade> findByBuyOrder_UserIdOrSellOrder_UserIdOrderByTimestampDesc(Long buyUserId, Long sellUserId);

    // Find trades by buy order ID
    List<Trade> findByBuyOrderIdOrderByTimestampDesc(Long buyOrderId);

    // Find trades by sell order ID
    List<Trade> findBySellOrderIdOrderByTimestampDesc(Long sellOrderId);
}