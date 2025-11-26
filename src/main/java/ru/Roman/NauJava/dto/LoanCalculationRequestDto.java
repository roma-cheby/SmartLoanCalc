package ru.Roman.NauJava.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import ru.Roman.NauJava.domain.enums.LoanCurrency;
import ru.Roman.NauJava.domain.enums.LoanType;
import ru.Roman.NauJava.domain.enums.PaymentType;
import ru.Roman.NauJava.domain.enums.RecalculationMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * DTO формы расчёта кредита.
 */
@Data
public class LoanCalculationRequestDto {

    @NotNull(message = "Тип кредита обязателен")
    private LoanType loanType = LoanType.CONSUMER;

    @NotNull(message = "Валюта обязательна")
    private LoanCurrency currency = LoanCurrency.RUB;

    @NotNull(message = "Сумма обязательна")
    @DecimalMin(value = "1000.00", message = "Минимальная сумма 1 000")
    @DecimalMax(value = "100000000.00", message = "Максимальная сумма 100 000 000")
    private BigDecimal principal;

    @NotNull(message = "Ставка обязательна")
    @DecimalMin(value = "0.1", message = "Ставка должна быть > 0")
    @DecimalMax(value = "99.99", message = "Ставка должна быть < 100")
    private BigDecimal interestRate;

    @Min(value = 1, message = "Минимум 1 месяц")
    @Max(value = 600, message = "Максимум 600 месяцев")
    private Integer durationMonths;

    @Min(value = 1, message = "Минимум 1 год")
    @Max(value = 50, message = "Максимум 50 лет")
    private Integer durationYears;

    @NotNull(message = "Тип платежей обязателен")
    private PaymentType paymentType = PaymentType.ANNUITY;

    @NotNull(message = "Дата выдачи обязательна")
    private LocalDate disbursementDate;

    @NotNull(message = "Дата первого платежа обязательна")
    private LocalDate firstPaymentDate;

    @NotNull(message = "Способ перерасчёта обязателен")
    private RecalculationMode recalculationMode = RecalculationMode.REDUCE_TERM;

    @Valid
    private List<RateChangeDto> rateChanges = new ArrayList<>();

    @Valid
    private List<EarlyPaymentDto> earlyPayments = new ArrayList<>();

    @Valid
    private List<PeriodicEarlyPaymentDto> periodicEarlyPayments = new ArrayList<>();

    private boolean saveToHistory;

    public int resolveDurationMonths() {
        if (durationMonths != null) {
            return durationMonths;
        }
        return durationYears != null ? durationYears * 12 : 0;
    }

    @AssertTrue(message = "Укажите срок в месяцах или годах")
    public boolean isDurationProvided() {
        return (durationMonths != null && durationMonths > 0)
                || (durationYears != null && durationYears > 0);
    }

    @AssertTrue(message = "Периоды изменения ставок не должны пересекаться")
    public boolean isRateTimelineValid() {
        if (rateChanges == null || rateChanges.isEmpty()) {
            return true;
        }
        List<RateChangeDto> sorted = rateChanges.stream()
                .filter(rc -> rc.getStartDate() != null)
                .sorted(Comparator.comparing(RateChangeDto::getStartDate))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (!sorted.get(i).getStartDate().isAfter(sorted.get(i - 1).getStartDate())) {
                return false;
            }
        }
        return true;
    }

    @AssertTrue(message = "Дата первого платежа должна быть не раньше даты выдачи")
    public boolean isFirstPaymentAfterDisbursement() {
        if (disbursementDate == null || firstPaymentDate == null) {
            return true;
        }
        return !firstPaymentDate.isBefore(disbursementDate);
    }
}

