package ru.Roman.NauJava.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import ru.Roman.NauJava.domain.enums.EarlyPaymentApplicationMode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO периодического досрочного платежа.
 */
@Data
public class PeriodicEarlyPaymentDto {

    @NotNull(message = "Дата старта обязательна")
    private LocalDate startDate;

    @NotNull(message = "Интервал обязателен")
    @Min(value = 1, message = "Минимальный интервал 1 месяц")
    @Max(value = 120, message = "Интервал не больше 120 месяцев")
    private Integer intervalMonths;

    @NotNull(message = "Сумма обязательна")
    @DecimalMin(value = "0.01", message = "Минимальная сумма 0.01")
    private BigDecimal amount;

    @NotNull(message = "Режим применения обязателен")
    private EarlyPaymentApplicationMode applicationMode = EarlyPaymentApplicationMode.ON_PAYMENT_DATE;
}

