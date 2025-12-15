package ru.Roman.NauJava.domain.enums;

/**
 * Базовые валюты кредита.
 */
public enum LoanCurrency {
    RUB,
    USD,
    EUR;

    public String getDisplayName() {
        return switch (this) {
            case RUB -> "Рубли (RUB)";
            case USD -> "Доллары (USD)";
            case EUR -> "Евро (EUR)";
        };
    }
}

