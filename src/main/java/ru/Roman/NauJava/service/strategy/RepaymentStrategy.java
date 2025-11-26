package ru.Roman.NauJava.service.strategy;

import ru.Roman.NauJava.domain.entity.PaymentScheduleItem;
import ru.Roman.NauJava.domain.enums.PaymentType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Стратегия расчёта графика погашения конкретного типа.
 */
public interface RepaymentStrategy {

    PaymentType supportedType();

    StrategyCalculationResult calculate(BigDecimal principal,
                                        BigDecimal annualRate,
                                        int durationMonths,
                                        Map<Integer, BigDecimal> earlyPayments);

    /**
     * Результат стратегии с графиком и агрегированными суммами.
     */
    record StrategyCalculationResult(List<PaymentScheduleItem> schedule,
                                     BigDecimal totalPayment,
                                     BigDecimal totalInterest) {
    }
}

