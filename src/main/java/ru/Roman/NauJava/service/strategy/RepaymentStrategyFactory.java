package ru.Roman.NauJava.service.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.Roman.NauJava.domain.enums.PaymentType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Регистрирует и выдаёт стратегии расчёта.
 */
@Component
@RequiredArgsConstructor
public class RepaymentStrategyFactory {

    private final List<RepaymentStrategy> strategies;
    private Map<PaymentType, RepaymentStrategy> cache;

    public RepaymentStrategy getStrategy(PaymentType type) {
        if (cache == null) {
            cache = new EnumMap<>(PaymentType.class);
            for (RepaymentStrategy strategy : strategies) {
                cache.put(strategy.supportedType(), strategy);
            }
        }
        RepaymentStrategy strategy = cache.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("Стратегия не найдена для типа " + type);
        }
        return strategy;
    }
}

