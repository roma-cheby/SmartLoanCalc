package ru.Roman.NauJava.service.strategy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class DifferentialRepaymentStrategyTest {

    private final DifferentialRepaymentStrategy strategy = new DifferentialRepaymentStrategy();

    @Test
    void shouldReduceDebtToZero() {
        RepaymentStrategy.StrategyCalculationResult result = strategy.calculate(
                new BigDecimal("150000"),
                new BigDecimal("10"),
                10,
                Collections.emptyMap()
        );

        assertThat(result.schedule()).isNotEmpty();
        assertThat(result.schedule().get(result.schedule().size() - 1).getRemainingDebt()).isZero();
        assertThat(result.totalPayment()).isGreaterThan(new BigDecimal("150000"));
    }
}

