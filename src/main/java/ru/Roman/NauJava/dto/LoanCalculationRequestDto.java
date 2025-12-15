package ru.Roman.NauJava.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import ru.Roman.NauJava.domain.enums.LoanCurrency;
import ru.Roman.NauJava.domain.enums.LoanType;
import ru.Roman.NauJava.domain.enums.PaymentType;
import ru.Roman.NauJava.domain.enums.RecalculationMode;
import ru.Roman.NauJava.domain.enums.SubsidyMode;

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

    @NotNull(message = "Срок кредита обязателен")
    @Min(value = 1, message = "Минимум 1 месяц")
    @Max(value = 600, message = "Максимум 600 месяцев")
    private Integer durationMonths;

    @NotNull(message = "Тип платежей обязателен")
    private PaymentType paymentType = PaymentType.ANNUITY;

    @NotNull(message = "Дата выдачи обязательна")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate disbursementDate;

    @NotNull(message = "Способ перерасчёта обязателен")
    private RecalculationMode recalculationMode = RecalculationMode.REDUCE_TERM;

    @Valid
    private List<RateChangeDto> rateChanges = new ArrayList<>();

    @Valid
    private List<EarlyPaymentDto> earlyPayments = new ArrayList<>();

    @Valid
    private List<PeriodicEarlyPaymentDto> periodicEarlyPayments = new ArrayList<>();

    private boolean saveToHistory;

    /**
     * Переносить даты платежей с выходных на ближайший будний день.
     */
    private boolean adjustWeekends = true;

    /**
     * Субсидированная ипотека от застройщика.
     */
    private boolean developerSubsidy = false;

    /**
     * Льготная ставка от застройщика (годовая, %).
     */
    @DecimalMin(value = "0.0", message = "Льготная ставка должна быть >= 0")
    @DecimalMax(value = "99.99", message = "Льготная ставка должна быть < 100")
    private BigDecimal subsidizedRate;

    /**
     * Срок субсидии в месяцах.
     */
    @Min(value = 1, message = "Минимальный срок субсидии 1 месяц")
    @Max(value = 600, message = "Максимальный срок субсидии 600 месяцев")
    private Integer subsidyDurationMonths;

    /**
     * Режим субсидии.
     */
    private SubsidyMode subsidyMode = SubsidyMode.FIXED_PAYMENT;

    /**
     * Ручной ввод платежа в льготный период (если указано — используется вместо расчёта).
     */
    @DecimalMin(value = "0.01", message = "Платёж должен быть > 0")
    private BigDecimal subsidizedPaymentAmount;

    public int resolveDurationMonths() {
        return durationMonths != null ? durationMonths : 0;
    }

    /**
     * Вычисляет дату первого платежа - через месяц после даты выдачи.
     */
    public LocalDate resolveFirstPaymentDate() {
        if (disbursementDate == null) {
            return null;
        }
        return disbursementDate.plusMonths(1);
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

}

