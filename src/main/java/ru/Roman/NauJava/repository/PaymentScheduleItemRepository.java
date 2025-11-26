package ru.Roman.NauJava.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.Roman.NauJava.domain.entity.PaymentScheduleItem;

/**
 * Репозиторий строк платёжного графика.
 */
public interface PaymentScheduleItemRepository extends JpaRepository<PaymentScheduleItem, Long> {
    void deleteByCalculationId(Long calculationId);
}

