package cto.apexmatch.matcher.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trades")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Trade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long buyOrderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyOrderId", insertable = false, updatable = false)
    private Order buyOrder;

    @Column(nullable = false)
    private Long sellOrderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sellOrderId", insertable = false, updatable = false)
    private Order sellOrder;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal quantity;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp = LocalDateTime.now();
}

