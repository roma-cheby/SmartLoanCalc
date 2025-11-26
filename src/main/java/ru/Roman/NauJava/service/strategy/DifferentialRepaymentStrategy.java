package ru.Roman.NauJava.service.strategy;

import org.springframework.stereotype.Component;
import ru.Roman.NauJava.domain.entity.PaymentScheduleItem;
import ru.Roman.NauJava.domain.enums.PaymentType;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Дифференцированная схема погашения.
 */
@Component
public class DifferentialRepaymentStrategy implements RepaymentStrategy {

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal MONTHS_IN_YEAR = new BigDecimal("12");

    @Override
    public PaymentType supportedType() {
        return PaymentType.DIFFERENTIAL;
    }

    @Override
    public StrategyCalculationResult calculate(BigDecimal principal,
                                               BigDecimal annualRate,
                                               int durationMonths,
                                               Map<Integer, BigDecimal> earlyPayments) {
        List<PaymentScheduleItem> schedule = new ArrayList<>();
        BigDecimal remaining = principal;
        BigDecimal monthlyRate = annualRate.divide(ONE_HUNDRED, MC).divide(MONTHS_IN_YEAR, MC);
        BigDecimal basePrincipalPart = principal.divide(BigDecimal.valueOf(durationMonths), 2, RoundingMode.HALF_UP);

        BigDecimal totalPayment = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;

        for (int month = 1; month <= durationMonths && remaining.compareTo(BigDecimal.ZERO) > 0; month++) {
            BigDecimal interestPart = remaining.multiply(monthlyRate, MC).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principalPart = basePrincipalPart.min(remaining);
            BigDecimal payment = principalPart.add(interestPart);

            remaining = remaining.subtract(principalPart);

            BigDecimal earlyPayment = earlyPayments.getOrDefault(month, BigDecimal.ZERO);
            if (earlyPayment.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal effective = earlyPayment.min(remaining);
                principalPart = principalPart.add(effective);
                payment = payment.add(effective);
                remaining = remaining.subtract(effective);
            }

            remaining = remaining.max(BigDecimal.ZERO);

            schedule.add(buildItem(month, payment, principalPart, interestPart, remaining));

            totalPayment = totalPayment.add(payment);
            totalInterest = totalInterest.add(interestPart);

            if (remaining.compareTo(new BigDecimal("0.01")) <= 0) {
                break;
            }
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal interestPart = remaining.multiply(monthlyRate, MC).setScale(2, RoundingMode.HALF_UP);
            BigDecimal payment = remaining.add(interestPart);
            schedule.add(buildItem(schedule.size() + 1, payment, remaining, interestPart, BigDecimal.ZERO));
            totalPayment = totalPayment.add(payment);
            totalInterest = totalInterest.add(interestPart);
            remaining = BigDecimal.ZERO;
        }

        return new StrategyCalculationResult(schedule,
                totalPayment.setScale(2, RoundingMode.HALF_UP),
                totalInterest.setScale(2, RoundingMode.HALF_UP));
    }

    private PaymentScheduleItem buildItem(int month, BigDecimal payment, BigDecimal principalPart,
                                          BigDecimal interestPart, BigDecimal remaining) {
        return PaymentScheduleItem.builder()
                .monthNumber(month)
                .paymentAmount(payment.setScale(2, RoundingMode.HALF_UP))
                .principalPart(principalPart.setScale(2, RoundingMode.HALF_UP))
                .interestPart(interestPart.setScale(2, RoundingMode.HALF_UP))
                .remainingDebt(remaining.setScale(2, RoundingMode.HALF_UP))
                .build();
    }
}

