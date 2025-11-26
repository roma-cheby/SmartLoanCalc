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
 * Реализация аннуитетной схемы.
 */
@Component
public class AnnuityRepaymentStrategy implements RepaymentStrategy {

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal MONTHS_IN_YEAR = new BigDecimal("12");

    @Override
    public PaymentType supportedType() {
        return PaymentType.ANNUITY;
    }

    @Override
    public StrategyCalculationResult calculate(BigDecimal principal,
                                               BigDecimal annualRate,
                                               int durationMonths,
                                               Map<Integer, BigDecimal> earlyPayments) {
        List<PaymentScheduleItem> schedule = new ArrayList<>();
        BigDecimal remaining = principal;
        BigDecimal monthlyRate = annualRate.divide(ONE_HUNDRED, MC).divide(MONTHS_IN_YEAR, MC);
        BigDecimal annuityPayment = computeAnnuityPayment(principal, monthlyRate, durationMonths);
        BigDecimal totalPayment = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;

        int month = 1;
        while (month <= durationMonths && remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal interestPart = remaining.multiply(monthlyRate, MC).setScale(2, RoundingMode.HALF_UP);
            BigDecimal payment = annuityPayment.min(remaining.add(interestPart));
            BigDecimal principalPart = payment.subtract(interestPart).max(BigDecimal.ZERO);

            if (principalPart.compareTo(remaining) > 0) {
                principalPart = remaining;
                payment = principalPart.add(interestPart);
            }

            remaining = remaining.subtract(principalPart);

            BigDecimal earlyPayment = earlyPayments.getOrDefault(month, BigDecimal.ZERO);
            if (earlyPayment.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal effectiveEarly = earlyPayment.min(remaining);
                principalPart = principalPart.add(effectiveEarly);
                payment = payment.add(effectiveEarly);
                remaining = remaining.subtract(effectiveEarly);
            }

            remaining = remaining.max(BigDecimal.ZERO);

            schedule.add(buildItem(month, payment, principalPart, interestPart, remaining));
            totalPayment = totalPayment.add(payment);
            totalInterest = totalInterest.add(interestPart);

            if (remaining.compareTo(new BigDecimal("0.01")) <= 0) {
                break;
            }
            month++;
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            // добиваем финальным платежом
            BigDecimal interestPart = remaining.multiply(monthlyRate, MC).setScale(2, RoundingMode.HALF_UP);
            BigDecimal payment = remaining.add(interestPart);
            schedule.add(buildItem(month, payment, remaining, interestPart, BigDecimal.ZERO));
            totalPayment = totalPayment.add(payment);
            totalInterest = totalInterest.add(interestPart);
            remaining = BigDecimal.ZERO;
        }

        return new StrategyCalculationResult(schedule, totalPayment.setScale(2, RoundingMode.HALF_UP),
                totalInterest.setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal computeAnnuityPayment(BigDecimal principal, BigDecimal monthlyRate, int durationMonths) {
        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(durationMonths), 2, RoundingMode.HALF_UP);
        }
        BigDecimal onePlusRatePow = (BigDecimal.ONE.add(monthlyRate)).pow(durationMonths, MC);
        BigDecimal numerator = principal.multiply(monthlyRate).multiply(onePlusRatePow, MC);
        BigDecimal denominator = onePlusRatePow.subtract(BigDecimal.ONE);
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
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

