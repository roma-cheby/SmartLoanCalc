package ru.Roman.NauJava.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.Roman.NauJava.domain.entity.EarlyPayment;
import ru.Roman.NauJava.domain.entity.LoanCalculation;
import ru.Roman.NauJava.domain.entity.PaymentScheduleItem;
import ru.Roman.NauJava.domain.entity.RateChange;
import ru.Roman.NauJava.domain.entity.User;
import ru.Roman.NauJava.domain.enums.EarlyPaymentApplicationMode;
import ru.Roman.NauJava.domain.enums.EarlyPaymentKind;
import ru.Roman.NauJava.domain.enums.PaymentType;
import ru.Roman.NauJava.domain.enums.RecalculationMode;
import ru.Roman.NauJava.domain.enums.UserRole;
import ru.Roman.NauJava.dto.*;
import ru.Roman.NauJava.mapper.LoanCalculationMapper;
import ru.Roman.NauJava.repository.LoanCalculationRepository;
import ru.Roman.NauJava.repository.UserRepository;
import ru.Roman.NauJava.repository.specification.LoanCalculationSpecifications;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис расчётов и управления историей.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanCalculationService {

    private final LoanCalculationRepository calculationRepository;
    private final UserRepository userRepository;
    private final LoanCalculationMapper calculationMapper;

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWELVE = new BigDecimal("12");
    private static final int MAX_CALCULATION_MONTHS = 720;
    private static final BigDecimal EPS = new BigDecimal("0.009");

    private record EarlyPaymentEvent(LocalDate date, BigDecimal amount, EarlyPaymentApplicationMode mode) {
    }

    private record RatePeriod(LocalDate start, BigDecimal rate) {
    }

    private record ScheduleResult(List<PaymentScheduleItem> schedule,
                                  BigDecimal totalPayment,
                                  BigDecimal totalInterest) {
    }

    /**
     * Выполняет расчёт и опционально сохраняет результат в историю.
     */
    @Transactional
    public LoanCalculationResponseDto calculate(LoanCalculationRequestDto request, @Nullable String username) {
        ScheduleResult result = buildSchedule(request);

        LoanCalculation calculation = LoanCalculation.builder()
                .loanType(request.getLoanType())
                .currency(request.getCurrency())
                .principal(request.getPrincipal().setScale(2, RoundingMode.HALF_UP))
                .interestRate(request.getInterestRate())
                .durationMonths(request.resolveDurationMonths())
                .paymentType(request.getPaymentType())
                .recalculationMode(request.getRecalculationMode())
                .disbursementDate(request.getDisbursementDate())
                .firstPaymentDate(request.getFirstPaymentDate())
                .totalInterest(result.totalInterest())
                .totalPayment(result.totalPayment())
                .build();

        attachEarlyPayments(calculation, request);
        attachRateChanges(calculation, request);
        attachSchedule(calculation, result.schedule());

        if (shouldPersist(request, username)) {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден: " + username));
            calculation.setUser(user);
            LoanCalculation saved = calculationRepository.save(calculation);
            log.info("Расчёт {} сохранён для пользователя {}", saved.getId(), username);
            return calculationMapper.toResponse(saved);
        }

        return calculationMapper.toResponse(calculation);
    }

    /**
     * Возвращает историю расчётов пользователя с фильтрами.
     */
    @Transactional(readOnly = true)
    public List<LoanCalculationHistoryDto> getHistory(String username, LoanCalculationFilterDto filter) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден: " + username));

        List<LoanCalculation> calculations;
        if (filter != null && filter.hasFilters()) {
            calculations = calculationRepository.findAll(
                    LoanCalculationSpecifications.build(filter, user.getRole() == UserRole.ADMIN ? null : username));
        } else if (user.getRole() == UserRole.ADMIN) {
            calculations = calculationRepository.findAll();
        } else {
            calculations = calculationRepository.findAllByUserUsernameOrderByCreatedAtDesc(username);
        }
        calculations.sort(Comparator.comparing(LoanCalculation::getCreatedAt).reversed());
        return calculationMapper.toHistoryList(calculations);
    }

    /**
     * Возвращает подробности сохранённого расчёта.
     */
    @Transactional(readOnly = true)
    public LoanCalculationResponseDto getCalculation(Long id, String username) {
        LoanCalculation calculation = loadForUser(id, username);
        return calculationMapper.toResponse(calculation);
    }

    /**
     * Удаляет расчёт из истории.
     */
    @Transactional
    public void delete(Long id, String username) {
        LoanCalculation calculation = loadForUser(id, username);
        calculationRepository.delete(calculation);
        log.info("Расчёт {} удалён пользователем {}", id, username);
    }

    /**
     * Повторяет расчёт по сохранённым параметрам, не затрагивая историю.
     */
    @Transactional(readOnly = true)
    public LoanCalculationResponseDto rerun(Long id, String username) {
        LoanCalculation calculation = loadForUser(id, username);
        LoanCalculationRequestDto request = new LoanCalculationRequestDto();
        request.setLoanType(calculation.getLoanType());
        request.setCurrency(calculation.getCurrency());
        request.setPrincipal(calculation.getPrincipal());
        request.setInterestRate(calculation.getInterestRate());
        request.setDurationMonths(calculation.getDurationMonths());
        request.setPaymentType(calculation.getPaymentType());
        request.setDisbursementDate(calculation.getDisbursementDate());
        request.setFirstPaymentDate(calculation.getFirstPaymentDate());
        request.setRecalculationMode(calculation.getRecalculationMode());
        request.setRateChanges(calculation.getRateChanges().stream()
                .map(rc -> {
                    RateChangeDto dto = new RateChangeDto();
                    dto.setStartDate(rc.getStartDate());
                    dto.setNewRate(rc.getNewRate());
                    return dto;
                })
                .collect(Collectors.toList()));
        request.setEarlyPayments(calculation.getEarlyPayments().stream()
                .filter(ep -> ep.getKind() == EarlyPaymentKind.ONE_TIME)
                .map(ep -> {
                    EarlyPaymentDto dto = new EarlyPaymentDto();
                    dto.setPaymentDate(ep.getPaymentDate());
                    dto.setAmount(ep.getAmount());
                    dto.setApplicationMode(ep.getApplicationMode());
                    return dto;
                })
                .collect(Collectors.toList()));
        request.setPeriodicEarlyPayments(calculation.getEarlyPayments().stream()
                .filter(ep -> ep.getKind() == EarlyPaymentKind.PERIODIC)
                .map(ep -> {
                    PeriodicEarlyPaymentDto dto = new PeriodicEarlyPaymentDto();
                    dto.setStartDate(ep.getStartDate());
                    dto.setIntervalMonths(ep.getIntervalMonths());
                    dto.setAmount(ep.getAmount());
                    dto.setApplicationMode(ep.getApplicationMode());
                    return dto;
                })
                .collect(Collectors.toList()));
        request.setSaveToHistory(false);

        return calculate(request, username);
    }

    private ScheduleResult buildSchedule(LoanCalculationRequestDto request) {
        List<EarlyPaymentEvent> events = expandEarlyPayments(request);
        List<RatePeriod> rateTimeline = buildRateTimeline(request);
        List<PaymentScheduleItem> schedule = new ArrayList<>();

        BigDecimal remaining = request.getPrincipal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalPayment = BigDecimal.ZERO;
        int originalDuration = request.resolveDurationMonths();
        if (originalDuration <= 0) {
            throw new IllegalArgumentException("Срок кредита должен быть положительным");
        }

        LocalDate paymentDate = request.getFirstPaymentDate();
        int monthIndex = 1;
        int eventsPointer = 0;
        BigDecimal currentRate = resolveRate(rateTimeline, paymentDate);
        BigDecimal currentPaymentAmount = request.getPaymentType() == PaymentType.ANNUITY
                ? calculateAnnuityPayment(remaining, currentRate, originalDuration)
                : BigDecimal.ZERO;

        while (remaining.compareTo(EPS) > 0 && monthIndex <= MAX_CALCULATION_MONTHS) {
            // apply between-payments events
            while (eventsPointer < events.size()) {
                EarlyPaymentEvent event = events.get(eventsPointer);
                if (event.date().isBefore(paymentDate)
                        || (event.date().isEqual(paymentDate)
                        && event.mode() == EarlyPaymentApplicationMode.BETWEEN_PAYMENTS)) {
                    eventsPointer++;
                    BigDecimal deducted = event.amount().min(remaining).setScale(2, RoundingMode.HALF_UP);
                    if (deducted.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    remaining = remaining.subtract(deducted);
                    totalPayment = totalPayment.add(deducted);
                    if (request.getPaymentType() == PaymentType.ANNUITY
                            && request.getRecalculationMode() == RecalculationMode.REDUCE_PAYMENT) {
                        int periodsLeft = Math.max(1, originalDuration - (monthIndex - 1));
                        currentPaymentAmount = calculateAnnuityPayment(remaining, currentRate, periodsLeft);
                    }
                    continue;
                }
                break;
            }

            BigDecimal resolvedRate = resolveRate(rateTimeline, paymentDate);
            if (request.getPaymentType() == PaymentType.ANNUITY && resolvedRate.compareTo(currentRate) != 0) {
                currentRate = resolvedRate;
                int periodsLeft = Math.max(1, originalDuration - (monthIndex - 1));
                currentPaymentAmount = calculateAnnuityPayment(remaining, currentRate, periodsLeft);
            } else {
                currentRate = resolvedRate;
            }

            BigDecimal monthlyRate = toMonthlyRate(currentRate);
            int periodsLeft = Math.max(1, originalDuration - (monthIndex - 1));
            BigDecimal paymentAmount;
            BigDecimal interestPart;
            BigDecimal principalPart;

            if (request.getPaymentType() == PaymentType.DIFFERENTIAL) {
                principalPart = remaining.divide(BigDecimal.valueOf(periodsLeft), MC)
                        .setScale(2, RoundingMode.HALF_UP)
                        .min(remaining);
                interestPart = remaining.multiply(monthlyRate, MC).setScale(2, RoundingMode.HALF_UP);
                paymentAmount = principalPart.add(interestPart);
            } else {
                paymentAmount = currentPaymentAmount;
                interestPart = remaining.multiply(monthlyRate, MC).setScale(2, RoundingMode.HALF_UP);
                principalPart = paymentAmount.subtract(interestPart);
                if (principalPart.compareTo(BigDecimal.ZERO) < 0) {
                    principalPart = BigDecimal.ZERO;
                }
            }

            if (principalPart.compareTo(remaining) > 0) {
                principalPart = remaining;
                paymentAmount = principalPart.add(interestPart);
            }

            remaining = remaining.subtract(principalPart);

            PaymentScheduleItem scheduleItem = PaymentScheduleItem.builder()
                    .monthNumber(monthIndex)
                    .paymentDate(paymentDate)
                    .paymentAmount(paymentAmount.setScale(2, RoundingMode.HALF_UP))
                    .principalPart(principalPart.setScale(2, RoundingMode.HALF_UP))
                    .interestPart(interestPart.setScale(2, RoundingMode.HALF_UP))
                    .remainingDebt(remaining.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP))
                    .build();
            schedule.add(scheduleItem);

            totalInterest = totalInterest.add(interestPart);
            totalPayment = totalPayment.add(paymentAmount);

            while (eventsPointer < events.size()) {
                EarlyPaymentEvent event = events.get(eventsPointer);
                if (event.date().isEqual(paymentDate)
                        && event.mode() == EarlyPaymentApplicationMode.ON_PAYMENT_DATE) {
                    eventsPointer++;
                    BigDecimal deducted = event.amount().min(remaining).setScale(2, RoundingMode.HALF_UP);
                    if (deducted.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    remaining = remaining.subtract(deducted);
                    totalPayment = totalPayment.add(deducted);
                    if (request.getPaymentType() == PaymentType.ANNUITY
                            && request.getRecalculationMode() == RecalculationMode.REDUCE_PAYMENT) {
                        int newPeriodsLeft = Math.max(1, originalDuration - monthIndex);
                        currentPaymentAmount = calculateAnnuityPayment(remaining, currentRate, newPeriodsLeft);
                    }
                    continue;
                }
                break;
            }

            paymentDate = paymentDate.plusMonths(1);
            monthIndex++;
        }

        if (remaining.compareTo(EPS) > 0) {
            throw new IllegalStateException("Не удалось досрочно погасить долг в допустимое количество шагов");
        }

        return new ScheduleResult(schedule,
                totalPayment.setScale(2, RoundingMode.HALF_UP),
                totalInterest.setScale(2, RoundingMode.HALF_UP));
    }

    private List<EarlyPaymentEvent> expandEarlyPayments(LoanCalculationRequestDto request) {
        List<EarlyPaymentEvent> events = new ArrayList<>();
        if (request.getEarlyPayments() != null) {
            for (EarlyPaymentDto dto : request.getEarlyPayments()) {
                if (dto.getPaymentDate() == null || dto.getAmount() == null) {
                    continue;
                }
                events.add(new EarlyPaymentEvent(dto.getPaymentDate(),
                        dto.getAmount().setScale(2, RoundingMode.HALF_UP),
                        dto.getApplicationMode()));
            }
        }
        if (request.getPeriodicEarlyPayments() != null) {
            LocalDate end = request.getFirstPaymentDate().plusMonths(MAX_CALCULATION_MONTHS);
            for (PeriodicEarlyPaymentDto dto : request.getPeriodicEarlyPayments()) {
                if (dto.getStartDate() == null || dto.getIntervalMonths() == null || dto.getAmount() == null) {
                    continue;
                }
                LocalDate date = dto.getStartDate();
                int guard = 0;
                while (!date.isAfter(end) && guard < MAX_CALCULATION_MONTHS) {
                    events.add(new EarlyPaymentEvent(date,
                            dto.getAmount().setScale(2, RoundingMode.HALF_UP),
                            dto.getApplicationMode()));
                    date = date.plusMonths(dto.getIntervalMonths());
                    guard++;
                }
            }
        }
        LocalDate disbursementDate = request.getDisbursementDate() != null
                ? request.getDisbursementDate()
                : request.getFirstPaymentDate();
        events.removeIf(event -> event.amount().compareTo(BigDecimal.ZERO) <= 0
                || (disbursementDate != null && event.date().isBefore(disbursementDate)));
        events.sort(Comparator.comparing(EarlyPaymentEvent::date));
        return events;
    }

    private List<RatePeriod> buildRateTimeline(LoanCalculationRequestDto request) {
        List<RatePeriod> ratePeriods = new ArrayList<>();
        ratePeriods.add(new RatePeriod(request.getFirstPaymentDate(), request.getInterestRate()));
        if (request.getRateChanges() != null) {
            for (RateChangeDto dto : request.getRateChanges()) {
                if (dto.getStartDate() == null || dto.getNewRate() == null) {
                    continue;
                }
                ratePeriods.add(new RatePeriod(dto.getStartDate(), dto.getNewRate()));
            }
        }
        ratePeriods.sort(Comparator.comparing(RatePeriod::start));
        return ratePeriods;
    }

    private BigDecimal resolveRate(List<RatePeriod> timeline, LocalDate date) {
        BigDecimal current = timeline.get(0).rate();
        for (RatePeriod period : timeline) {
            if (!date.isBefore(period.start())) {
                current = period.rate();
            } else {
                break;
            }
        }
        return current;
    }

    private BigDecimal calculateAnnuityPayment(BigDecimal principal, BigDecimal annualRate, int months) {
        if (months <= 0) {
            return principal.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal monthlyRate = toMonthlyRate(annualRate);
        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        }
        BigDecimal onePlusRatePow = BigDecimal.ONE.add(monthlyRate).pow(months, MC);
        BigDecimal numerator = principal.multiply(monthlyRate, MC).multiply(onePlusRatePow, MC);
        BigDecimal denominator = onePlusRatePow.subtract(BigDecimal.ONE);
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        }
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal toMonthlyRate(BigDecimal annualRate) {
        return annualRate.divide(ONE_HUNDRED, MC).divide(TWELVE, MC);
    }

    private void attachEarlyPayments(LoanCalculation calculation, LoanCalculationRequestDto request) {
        calculation.getEarlyPayments().clear();
        if (request.getEarlyPayments() != null) {
            for (EarlyPaymentDto dto : request.getEarlyPayments()) {
                if (dto.getPaymentDate() == null || dto.getAmount() == null) {
                    continue;
                }
                int monthNumber = (int) Math.max(1, ChronoUnit.MONTHS.between(
                        request.getFirstPaymentDate(), dto.getPaymentDate()) + 1);
                EarlyPayment entity = EarlyPayment.builder()
                        .calculation(calculation)
                        .monthNumber(monthNumber)
                        .kind(EarlyPaymentKind.ONE_TIME)
                        .applicationMode(dto.getApplicationMode())
                        .paymentDate(dto.getPaymentDate())
                        .amount(dto.getAmount().setScale(2, RoundingMode.HALF_UP))
                        .build();
                calculation.getEarlyPayments().add(entity);
            }
        }
        if (request.getPeriodicEarlyPayments() != null) {
            for (PeriodicEarlyPaymentDto dto : request.getPeriodicEarlyPayments()) {
                if (dto.getStartDate() == null || dto.getIntervalMonths() == null || dto.getAmount() == null) {
                    continue;
                }
                EarlyPayment entity = EarlyPayment.builder()
                        .calculation(calculation)
                        .monthNumber(0)
                        .kind(EarlyPaymentKind.PERIODIC)
                        .applicationMode(dto.getApplicationMode())
                        .startDate(dto.getStartDate())
                        .intervalMonths(dto.getIntervalMonths())
                        .amount(dto.getAmount().setScale(2, RoundingMode.HALF_UP))
                        .build();
                calculation.getEarlyPayments().add(entity);
            }
        }
    }

    private void attachRateChanges(LoanCalculation calculation, LoanCalculationRequestDto request) {
        calculation.getRateChanges().clear();
        if (request.getRateChanges() == null) {
            return;
        }
        for (RateChangeDto dto : request.getRateChanges()) {
            if (dto.getStartDate() == null || dto.getNewRate() == null) {
                continue;
            }
            RateChange entity = RateChange.builder()
                    .calculation(calculation)
                    .startDate(dto.getStartDate())
                    .newRate(dto.getNewRate().setScale(2, RoundingMode.HALF_UP))
                    .build();
            calculation.getRateChanges().add(entity);
        }
    }

    private void attachSchedule(LoanCalculation calculation, List<PaymentScheduleItem> schedule) {
        calculation.getScheduleItems().clear();
        for (PaymentScheduleItem item : schedule) {
            item.setCalculation(calculation);
            calculation.getScheduleItems().add(item);
        }
    }

    private boolean shouldPersist(LoanCalculationRequestDto request, String username) {
        return username != null && request.isSaveToHistory();
    }

    private LoanCalculation loadForUser(Long id, String username) {
        if (username == null) {
            throw new AccessDeniedException("Неавторизованный доступ к истории запрещён");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));
        if (user.getRole() == UserRole.ADMIN) {
            return calculationRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Расчёт не найден"));
        }
        return calculationRepository.findByIdAndUserUsername(id, username)
                .orElseThrow(() -> new EntityNotFoundException("Расчёт не найден или недоступен"));
    }
}

