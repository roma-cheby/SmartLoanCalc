package ru.Roman.NauJava.domain.enums;

/**
 * Режим субсидии от застройщика.
 */
public enum SubsidyMode {
    /**
     * Фиксированный платёж в период субсидии (самый распространённый в РФ).
     * Платёж = аннуитет по льготной ставке.
     */
    FIXED_PAYMENT("Фиксированный платёж"),

    /**
     * Плавающий платёж в период субсидии.
     * Основной долг = как по полной ставке, проценты = по льготной.
     */
    FLOATING_PAYMENT("Плавающий платёж");

    private final String displayName;

    SubsidyMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

