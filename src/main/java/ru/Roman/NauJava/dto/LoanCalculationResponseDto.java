package ru.Roman.NauJava.dto;

import lombok.Builder;
import lombok.Value;
import ru.Roman.NauJava.domain.enums.LoanCurrency;
import ru.Roman.NauJava.domain.enums.LoanType;
import ru.Roman.NauJava.domain.enums.PaymentType;
import ru.Roman.NauJava.domain.enums.RecalculationMode;
import ru.Roman.NauJava.domain.enums.SubsidyMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO ответа с агрегированными итогами и графиком.
 */
@Value
@Builder
public class LoanCalculationResponseDto {
    Long id;
    LoanType loanType;
    PaymentType paymentType;
    LoanCurrency currency;
    BigDecimal principal;
    BigDecimal interestRate;
    Integer durationMonths;
    RecalculationMode recalculationMode;
    LocalDate disbursementDate;
    LocalDate firstPaymentDate;
    boolean adjustWeekends;
    
    // Субсидия от застройщика
    boolean developerSubsidy;
    BigDecimal subsidizedRate;
    Integer subsidyDurationMonths;
    SubsidyMode subsidyMode;
    BigDecimal totalSubsidy; // Общая сумма субсидии от застройщика
    BigDecimal subsidizedPayment; // Платёж в период субсидии
    BigDecimal fullPayment; // Платёж после окончания субсидии
    BigDecimal balanceAfterSubsidy; // Остаток долга после субсидии
    
    BigDecimal totalInterest;
    BigDecimal totalPayment;
    LocalDateTime createdAt;
    List<PaymentScheduleItemDto> schedule;
}

