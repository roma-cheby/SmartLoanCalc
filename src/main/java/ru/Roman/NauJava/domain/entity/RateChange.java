package ru.Roman.NauJava.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Период с изменённой процентной ставкой.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rate_changes")
public class RateChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculation_id", nullable = false)
    private LoanCalculation calculation;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal newRate;
}

