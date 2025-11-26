package ru.Roman.NauJava.service.strategy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class AnnuityRepaymentStrategyTest {

    private final AnnuityRepaymentStrategy strategy = new AnnuityRepaymentStrategy();

    @Test
    void shouldCalculateSchedule() {
        RepaymentStrategy.StrategyCalculationResult result = strategy.calculate(
                new BigDecimal("100000"),
                new BigDecimal("12"),
                12,
                Collections.emptyMap()
        );

        assertThat(result.schedule()).hasSize(12);
        assertThat(result.totalPayment()).isGreaterThan(new BigDecimal("100000"));
        assertThat(result.schedule().get(result.schedule().size() - 1).getRemainingDebt()).isZero();
    }
}

