package ru.Roman.NauJava.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.Roman.NauJava.domain.entity.LoanCalculation;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с расчётами и их фильтрацией через спецификации.
 */
public interface LoanCalculationRepository extends JpaRepository<LoanCalculation, Long>,
        JpaSpecificationExecutor<LoanCalculation> {

    List<LoanCalculation> findAllByUserUsernameOrderByCreatedAtDesc(String username);

    Optional<LoanCalculation> findByIdAndUserUsername(Long id, String username);
}

