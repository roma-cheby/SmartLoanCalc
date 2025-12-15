package ru.Roman.NauJava.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO для строки платежного графика.
 */
@Value
@Builder
public class PaymentScheduleItemDto {
    Integer monthNumber;
    LocalDate paymentDate;
    BigDecimal paymentAmount;
    BigDecimal principalPart;
    BigDecimal interestPart;
    BigDecimal remainingDebt;
    BigDecimal subsidyAmount; // Субсидия от застройщика за этот месяц
    boolean earlyPayment; // Флаг досрочного платежа
}

