package ru.Roman.NauJava.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import ru.Roman.NauJava.domain.enums.LoanCurrency;
import ru.Roman.NauJava.domain.enums.LoanType;
import ru.Roman.NauJava.domain.enums.PaymentType;
import ru.Roman.NauJava.domain.enums.RecalculationMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Хранит параметры выполненного расчёта и сгенерированный график платежей.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "loan_calculations")
public class LoanCalculation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LoanType loanType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private LoanCurrency currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal principal;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(nullable = false)
    private Integer durationMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecalculationMode recalculationMode;

    @Column(nullable = false)
    private LocalDate disbursementDate;

    @Column(nullable = false)
    private LocalDate firstPaymentDate;

    @Builder.Default
    @OneToMany(mappedBy = "calculation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EarlyPayment> earlyPayments = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "calculation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RateChange> rateChanges = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "calculation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("monthNumber ASC")
    private List<PaymentScheduleItem> scheduleItems = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalInterest;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPayment;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

