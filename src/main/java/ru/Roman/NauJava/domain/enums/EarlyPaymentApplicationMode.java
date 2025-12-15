package ru.Roman.NauJava.domain.enums;

/**
 * Когда списывается досрочный платёж.
 */
public enum EarlyPaymentApplicationMode {
    ON_PAYMENT_DATE,
    BETWEEN_PAYMENTS;

    public String getDisplayName() {
        return switch (this) {
            case ON_PAYMENT_DATE -> "В дату платежа";
            case BETWEEN_PAYMENTS -> "Между платежами";
        };
    }
}

