package ru.Roman.NauJava.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.Roman.NauJava.domain.entity.EarlyPayment;

/**
 * Репозиторий досрочных платежей.
 */
public interface EarlyPaymentRepository extends JpaRepository<EarlyPayment, Long> {
    void deleteByCalculationId(Long calculationId);
}

