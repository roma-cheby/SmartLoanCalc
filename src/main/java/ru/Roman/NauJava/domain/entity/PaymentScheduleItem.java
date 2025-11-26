package ru.Roman.NauJava.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Представляет одну строку платежного графика по результатам расчёта.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment_schedule")
public class PaymentScheduleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculation_id", nullable = false)
    private LoanCalculation calculation;

    @Column(nullable = false)
    private Integer monthNumber;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal paymentAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal principalPart;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal interestPart;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal remainingDebt;
}

