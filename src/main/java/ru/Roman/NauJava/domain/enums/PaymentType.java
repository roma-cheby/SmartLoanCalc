package ru.Roman.NauJava.domain.enums;

/**
 * Схемы погашения кредита.
 */
public enum PaymentType {
    ANNUITY,
    DIFFERENTIAL;

    public String getDisplayName() {
        return switch (this) {
            case ANNUITY -> "Аннуитетный";
            case DIFFERENTIAL -> "Дифференцированный";
        };
    }
}

