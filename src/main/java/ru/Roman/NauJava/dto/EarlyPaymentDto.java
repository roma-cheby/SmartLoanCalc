package ru.Roman.NauJava.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import ru.Roman.NauJava.domain.enums.EarlyPaymentApplicationMode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO для описания одного досрочного платежа в форме/REST.
 */
@Data
public class EarlyPaymentDto {

    @NotNull(message = "Дата платежа обязательна")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate paymentDate;

    @NotNull(message = "Сумма обязательна")
    @DecimalMin(value = "0.01", message = "Минимальная сумма 0.01")
    private BigDecimal amount;

    @NotNull(message = "Режим применения обязателен")
    private EarlyPaymentApplicationMode applicationMode = EarlyPaymentApplicationMode.ON_PAYMENT_DATE;
}

