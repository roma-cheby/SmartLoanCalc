package ru.Roman.NauJava.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import ru.Roman.NauJava.domain.enums.EarlyPaymentApplicationMode;
import ru.Roman.NauJava.domain.enums.EarlyPaymentKind;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Описывает досрочный взнос в определённый месяц графика.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "early_payments")
public class EarlyPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculation_id", nullable = false)
    private LoanCalculation calculation;

    @Column(nullable = false)
    private Integer monthNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EarlyPaymentKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EarlyPaymentApplicationMode applicationMode;

    private LocalDate paymentDate;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer intervalMonths;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
}

