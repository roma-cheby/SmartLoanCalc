package ru.Roman.NauJava.repository.specification;

import org.springframework.data.jpa.domain.Specification;
import ru.Roman.NauJava.domain.entity.LoanCalculation;
import ru.Roman.NauJava.dto.LoanCalculationFilterDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Набор спецификаций JPA для гибкой фильтрации истории расчётов.
 */
public final class LoanCalculationSpecifications {

    private LoanCalculationSpecifications() {
    }

    public static Specification<LoanCalculation> build(LoanCalculationFilterDto filter, String username) {
        Specification<LoanCalculation> spec = Specification.where(null);
        if (username != null) {
            spec = spec.and(belongsToUser(username));
        }

        if (filter == null) {
            return spec;
        }
        if (filter.getFromDate() != null) {
            spec = spec.and(createdAfter(filter.getFromDate()));
        }
        if (filter.getToDate() != null) {
            spec = spec.and(createdBefore(filter.getToDate()));
        }
        if (filter.getLoanType() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("loanType"), filter.getLoanType()));
        }
        if (filter.getPaymentType() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("paymentType"), filter.getPaymentType()));
        }
        if (filter.getCurrency() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("currency"), filter.getCurrency()));
        }
        if (filter.getMinPrincipal() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("principal"), filter.getMinPrincipal()));
        }
        if (filter.getMaxPrincipal() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("principal"), filter.getMaxPrincipal()));
        }
        if (filter.getMinRate() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("interestRate"), filter.getMinRate()));
        }
        if (filter.getMaxRate() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("interestRate"), filter.getMaxRate()));
        }
        return spec;
    }

    public static Specification<LoanCalculation> belongsToUser(String username) {
        return (root, query, cb) -> cb.equal(root.join("user").get("username"), username);
    }

    private static Specification<LoanCalculation> createdAfter(LocalDate fromDate) {
        LocalDateTime start = fromDate.atStartOfDay();
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start);
    }

    private static Specification<LoanCalculation> createdBefore(LocalDate toDate) {
        LocalDateTime end = LocalDateTime.of(toDate, LocalTime.MAX);
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), end);
    }
}

