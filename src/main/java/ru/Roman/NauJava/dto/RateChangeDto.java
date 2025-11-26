package ru.Roman.NauJava.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO изменения процентной ставки.
 */
@Data
public class RateChangeDto {

    @NotNull(message = "Дата начала периода обязательна")
    private LocalDate startDate;

    @NotNull(message = "Новая ставка обязательна")
    @DecimalMin(value = "0.1", message = "Ставка должна быть > 0")
    private BigDecimal newRate;
}

