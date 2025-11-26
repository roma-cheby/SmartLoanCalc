package ru.Roman.NauJava.mapper;

import org.springframework.stereotype.Component;
import ru.Roman.NauJava.domain.entity.LoanCalculation;
import ru.Roman.NauJava.domain.entity.PaymentScheduleItem;
import ru.Roman.NauJava.dto.LoanCalculationHistoryDto;
import ru.Roman.NauJava.dto.LoanCalculationResponseDto;
import ru.Roman.NauJava.dto.PaymentScheduleItemDto;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Простой вручную реализованный маппер между сущностями и DTO.
 */
@Component
public class LoanCalculationMapper {

    public LoanCalculationResponseDto toResponse(LoanCalculation calculation) {
        if (calculation == null) {
            return null;
        }
        return LoanCalculationResponseDto.builder()
                .id(calculation.getId())
                .loanType(calculation.getLoanType())
                .paymentType(calculation.getPaymentType())
                .currency(calculation.getCurrency())
                .principal(calculation.getPrincipal())
                .interestRate(calculation.getInterestRate())
                .durationMonths(calculation.getDurationMonths())
                .recalculationMode(calculation.getRecalculationMode())
                .disbursementDate(calculation.getDisbursementDate())
                .firstPaymentDate(calculation.getFirstPaymentDate())
                .totalInterest(calculation.getTotalInterest())
                .totalPayment(calculation.getTotalPayment())
                .createdAt(calculation.getCreatedAt())
                .schedule(toScheduleDto(calculation.getScheduleItems()))
                .build();
    }

    public LoanCalculationHistoryDto toHistory(LoanCalculation calculation) {
        if (calculation == null) {
            return null;
        }
        return LoanCalculationHistoryDto.builder()
                .id(calculation.getId())
                .loanType(calculation.getLoanType())
                .paymentType(calculation.getPaymentType())
                .currency(calculation.getCurrency())
                .principal(calculation.getPrincipal())
                .interestRate(calculation.getInterestRate())
                .durationMonths(calculation.getDurationMonths())
                .totalInterest(calculation.getTotalInterest())
                .totalPayment(calculation.getTotalPayment())
                .createdAt(calculation.getCreatedAt())
                .build();
    }

    public List<LoanCalculationHistoryDto> toHistoryList(List<LoanCalculation> calculations) {
        if (calculations == null) {
            return Collections.emptyList();
        }
        return calculations.stream()
                .filter(Objects::nonNull)
                .map(this::toHistory)
                .collect(Collectors.toList());
    }

    public List<PaymentScheduleItemDto> toScheduleDto(List<PaymentScheduleItem> scheduleItems) {
        if (scheduleItems == null) {
            return Collections.emptyList();
        }
        return scheduleItems.stream()
                .filter(Objects::nonNull)
                .map(this::toScheduleItemDto)
                .collect(Collectors.toList());
    }

    private PaymentScheduleItemDto toScheduleItemDto(PaymentScheduleItem item) {
        return PaymentScheduleItemDto.builder()
                .monthNumber(item.getMonthNumber())
                .paymentDate(item.getPaymentDate())
                .paymentAmount(item.getPaymentAmount())
                .principalPart(item.getPrincipalPart())
                .interestPart(item.getInterestPart())
                .remainingDebt(item.getRemainingDebt())
                .build();
    }
}

