package ru.Roman.NauJava.dto;

import lombok.Builder;
import lombok.Value;
import ru.Roman.NauJava.domain.enums.LoanCurrency;
import ru.Roman.NauJava.domain.enums.LoanType;
import ru.Roman.NauJava.domain.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO краткого представления расчёта для истории.
 */
@Value
@Builder
public class LoanCalculationHistoryDto {
    Long id;
    LoanType loanType;
    PaymentType paymentType;
    LoanCurrency currency;
    BigDecimal principal;
    BigDecimal interestRate;
    Integer durationMonths;
    BigDecimal totalInterest;
    BigDecimal totalPayment;
    LocalDateTime createdAt;
}

