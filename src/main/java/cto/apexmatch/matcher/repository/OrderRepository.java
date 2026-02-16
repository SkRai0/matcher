package cto.apexmatch.matcher.repository;

import cto.apexmatch.matcher.model.Order;
import cto.apexmatch.matcher.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Find orders by user and status (for user's order history)
    List<Order> findByUserIdAndStatusIn(Long userId, List<OrderStatus> statuses);

    // Find orders by symbol and status (for order book)
    List<Order> findBySymbolAndStatusIn(String symbol, List<OrderStatus> statuses);

    // Find buy orders sorted by price DESC, then time ASC (highest price first)
    @Query("SELECT o FROM Order o WHERE o.symbol = :symbol AND o.type = 'BUY' AND o.status IN :statuses ORDER BY o.price DESC, o.createdAt ASC")
    List<Order> findBuyOrdersBySymbol(@Param("symbol") String symbol, @Param("statuses") List<OrderStatus> statuses);

    // Find sell orders sorted by price ASC, then time ASC (lowest price first)
    @Query("SELECT o FROM Order o WHERE o.symbol = :symbol AND o.type = 'SELL' AND o.status IN :statuses ORDER BY o.price ASC, o.createdAt ASC")
    List<Order> findSellOrdersBySymbol(@Param("symbol") String symbol, @Param("statuses") List<OrderStatus> statuses);
}