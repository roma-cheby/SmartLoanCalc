package ru.Roman.NauJava.domain.enums;

/**
 * Варианты перерасчёта графика после досрочного платежа.
 */
public enum RecalculationMode {
    REDUCE_TERM,
    REDUCE_PAYMENT;

    public String getDisplayName() {
        return switch (this) {
            case REDUCE_TERM -> "Уменьшение срока";
            case REDUCE_PAYMENT -> "Уменьшение платежа";
        };
    }
}

