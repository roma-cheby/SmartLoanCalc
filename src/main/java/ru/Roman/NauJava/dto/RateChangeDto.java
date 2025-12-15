package ru.Roman.NauJava.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO изменения процентной ставки.
 */
@Data
public class RateChangeDto {

    @NotNull(message = "Дата начала периода обязательна")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @NotNull(message = "Новая ставка обязательна")
    @DecimalMin(value = "0.1", message = "Ставка должна быть > 0")
    private BigDecimal newRate;
}

