package ru.Roman.NauJava.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import ru.Roman.NauJava.domain.enums.LoanCurrency;
import ru.Roman.NauJava.domain.enums.LoanType;
import ru.Roman.NauJava.domain.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO фильтрации истории расчётов.
 */
@Data
public class LoanCalculationFilterDto {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    private LoanType loanType;
    private PaymentType paymentType;
    private LoanCurrency currency;
    private BigDecimal minPrincipal;
    private BigDecimal maxPrincipal;
    private BigDecimal minRate;
    private BigDecimal maxRate;

    public boolean hasFilters() {
        return fromDate != null || toDate != null || loanType != null || paymentType != null
                || currency != null || minPrincipal != null || maxPrincipal != null
                || minRate != null || maxRate != null;
    }
}
