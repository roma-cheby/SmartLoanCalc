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
import ru.Roman.NauJava.domain.enums.SubsidyMode;
import ru.Roman.NauJava.domain.enums.UserRole;
import ru.Roman.NauJava.dto.*;
import ru.Roman.NauJava.mapper.LoanCalculationMapper;
import ru.Roman.NauJava.repository.LoanCalculationRepository;
import ru.Roman.NauJava.repository.UserRepository;
import ru.Roman.NauJava.repository.specification.LoanCalculationSpecifications;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.DayOfWeek;
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
                                  BigDecimal totalInterest,
                                  BigDecimal totalSubsidy,
                                  BigDecimal subsidizedPayment,
                                  BigDecimal fullPayment,
                                  BigDecimal balanceAfterSubsidy) {
    }

    /**
     * Выполняет расчёт и опционально сохраняет результат в историю.
     */
    @Transactional
    public LoanCalculationResponseDto calculate(LoanCalculationRequestDto request, @Nullable String username) {
        if (request.getDisbursementDate() == null) {
            throw new IllegalArgumentException("Дата выдачи обязательна");
        }
        if (request.resolveFirstPaymentDate() == null) {
            throw new IllegalArgumentException("Не удалось вычислить дату первого платежа");
        }
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
                .firstPaymentDate(request.resolveFirstPaymentDate())
                .adjustWeekends(request.isAdjustWeekends())
                .developerSubsidy(request.isDeveloperSubsidy())
                .subsidizedRate(request.getSubsidizedRate())
                .subsidyDurationMonths(request.getSubsidyDurationMonths())
                .subsidyMode(request.getSubsidyMode())
                .totalSubsidy(result.totalSubsidy())
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
            return calculationMapper.toResponse(saved, result.subsidizedPayment(), result.fullPayment(), result.balanceAfterSubsidy());
        }

        return calculationMapper.toResponse(calculation, result.subsidizedPayment(), result.fullPayment(), result.balanceAfterSubsidy());
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
        request.setRecalculationMode(calculation.getRecalculationMode());
        request.setAdjustWeekends(calculation.isAdjustWeekends());
        request.setDeveloperSubsidy(calculation.isDeveloperSubsidy());
        request.setSubsidizedRate(calculation.getSubsidizedRate());
        request.setSubsidyDurationMonths(calculation.getSubsidyDurationMonths());
        request.setSubsidyMode(calculation.getSubsidyMode());
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
                    dto.setEndDate(ep.getEndDate());
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
        // Если субсидированная ипотека - используем специальный алгоритм
        // Требуется: срок субсидии + (ручной платёж ИЛИ льготная ставка)
        if (request.isDeveloperSubsidy() 
                && request.getSubsidyDurationMonths() != null && request.getSubsidyDurationMonths() > 0
                && (request.getSubsidizedPaymentAmount() != null || request.getSubsidizedRate() != null)) {
            return buildSubsidizedSchedule(request);
        }
        
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

        boolean adjustWeekends = request.isAdjustWeekends();
        LocalDate paymentDate = adjustForWeekend(request.resolveFirstPaymentDate(), adjustWeekends);
        LocalDate previousPaymentDate = request.getDisbursementDate(); // Начало первого периода - дата выдачи
        int monthIndex = 1;
        int eventsPointer = 0;
        BigDecimal currentRate = resolveRate(rateTimeline, paymentDate);
        BigDecimal currentPaymentAmount = request.getPaymentType() == PaymentType.ANNUITY
                ? calculateAnnuityPayment(remaining, currentRate, originalDuration)
                : BigDecimal.ZERO;

        while (remaining.compareTo(EPS) > 0 && monthIndex <= MAX_CALCULATION_MONTHS) {
            // apply between-payments events (только BETWEEN_PAYMENTS с датой <= даты платежа)
            while (eventsPointer < events.size()) {
                EarlyPaymentEvent event = events.get(eventsPointer);
                // BETWEEN_PAYMENTS: применяем если дата события <= даты платежа
                // ON_PAYMENT_DATE: пропускаем здесь, обработаем после регулярного платежа
                boolean shouldApply = event.mode() == EarlyPaymentApplicationMode.BETWEEN_PAYMENTS 
                        && !event.date().isAfter(paymentDate);
                if (shouldApply) {
                    eventsPointer++;
                    BigDecimal deducted = event.amount().min(remaining).setScale(2, RoundingMode.HALF_UP);
                    if (deducted.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    remaining = remaining.subtract(deducted);
                    totalPayment = totalPayment.add(deducted);
                    log.debug("Применён досрочный платёж (между): {} на дату {}, новый остаток: {}", deducted, event.date(), remaining);
                    
                    // Добавляем строку досрочного платежа в график
                    PaymentScheduleItem earlyPaymentItem = PaymentScheduleItem.builder()
                            .monthNumber(0) // Без номера
                            .paymentDate(event.date())
                            .paymentAmount(deducted)
                            .principalPart(deducted) // Весь платёж идёт в основной долг
                            .interestPart(BigDecimal.ZERO)
                            .remainingDebt(remaining.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP))
                            .earlyPayment(true)
                            .build();
                    schedule.add(earlyPaymentItem);
                    
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

            int periodsLeft = Math.max(1, originalDuration - (monthIndex - 1));
            BigDecimal paymentAmount;
            BigDecimal interestPart;
            BigDecimal principalPart;

            // Расчёт процентов по фактическому количеству дней в периоде
            interestPart = calculateInterestForPeriod(remaining, currentRate, previousPaymentDate, paymentDate);

            if (request.getPaymentType() == PaymentType.DIFFERENTIAL) {
                principalPart = remaining.divide(BigDecimal.valueOf(periodsLeft), MC)
                        .setScale(2, RoundingMode.HALF_UP)
                        .min(remaining);
                paymentAmount = principalPart.add(interestPart);
            } else {
                // Аннуитетный платёж
                paymentAmount = currentPaymentAmount;
                principalPart = paymentAmount.subtract(interestPart);
                
                // Если проценты >= платежа, то основной долг = 0
                if (principalPart.compareTo(BigDecimal.ZERO) <= 0) {
                    principalPart = BigDecimal.ZERO;
                    // Для ипотеки: платим фиксированный аннуитет, но если проценты больше - только проценты
                    if (interestPart.compareTo(currentPaymentAmount) > 0) {
                        paymentAmount = interestPart;
                    } else {
                        paymentAmount = currentPaymentAmount;
                    }
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
                    log.debug("Применён досрочный платёж (в дату): {} на дату {}, новый остаток: {}", deducted, event.date(), remaining);
                    
                    // Добавляем строку досрочного платежа в график
                    PaymentScheduleItem earlyPaymentItem = PaymentScheduleItem.builder()
                            .monthNumber(0) // Без номера
                            .paymentDate(event.date())
                            .paymentAmount(deducted)
                            .principalPart(deducted) // Весь платёж идёт в основной долг
                            .interestPart(BigDecimal.ZERO)
                            .remainingDebt(remaining.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP))
                            .earlyPayment(true)
                            .build();
                    schedule.add(earlyPaymentItem);
                    
                    if (request.getPaymentType() == PaymentType.ANNUITY
                            && request.getRecalculationMode() == RecalculationMode.REDUCE_PAYMENT) {
                        int newPeriodsLeft = Math.max(1, originalDuration - monthIndex);
                        currentPaymentAmount = calculateAnnuityPayment(remaining, currentRate, newPeriodsLeft);
                    }
                    continue;
                }
                break;
            }

            previousPaymentDate = paymentDate;
            monthIndex++;
            // Вычисляем следующую дату платежа от первоначальной даты выдачи, чтобы сохранить день месяца
            LocalDate nextPaymentDate = request.getDisbursementDate().plusMonths(monthIndex);
            paymentDate = adjustForWeekend(nextPaymentDate, adjustWeekends);
        }

        if (remaining.compareTo(EPS) > 0) {
            throw new IllegalStateException("Не удалось досрочно погасить долг в допустимое количество шагов");
        }
        
        log.debug("Расчёт завершён: платежей={}, общая выплата={}, переплата={}", schedule.size(), totalPayment, totalInterest);

        return new ScheduleResult(schedule,
                totalPayment.setScale(2, RoundingMode.HALF_UP),
                totalInterest.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO, // totalSubsidy
                null, // subsidizedPayment
                null, // fullPayment
                null); // balanceAfterSubsidy
    }

    /**
     * Расчёт субсидированной ипотеки от застройщика.
     * Алгоритм:
     * 1. A_full = аннуитет по полной ставке банка
     * 2. A_sub = аннуитет по льготной ставке (для FIXED_PAYMENT)
     * 3. В период субсидии: проценты рассчитываются ПО ДНЯМ по льготной ставке
     * 4. Если проценты > платежа → осн.долг = 0
     * 5. После субсидии: стандартный платёж A_full, проценты по дням по полной ставке
     */
    private ScheduleResult buildSubsidizedSchedule(LoanCalculationRequestDto request) {
        List<PaymentScheduleItem> schedule = new ArrayList<>();
        List<EarlyPaymentEvent> events = expandEarlyPayments(request);
        
        BigDecimal principal = request.getPrincipal().setScale(2, RoundingMode.HALF_UP);
        int durationMonths = request.resolveDurationMonths();
        BigDecimal fullRate = request.getInterestRate(); // Полная ставка банка (годовая)
        BigDecimal subsidizedRate = request.getSubsidizedRate(); // Льготная ставка (годовая), может быть null
        int subsidyDuration = request.getSubsidyDurationMonths();
        SubsidyMode subsidyMode = request.getSubsidyMode() != null ? request.getSubsidyMode() : SubsidyMode.FIXED_PAYMENT;
        boolean adjustWeekends = request.isAdjustWeekends();
        
        // Годовые ставки для расчёта по дням
        BigDecimal yearlyRateFull = fullRate.divide(ONE_HUNDRED, MC);
        // Если льготная ставка не указана, используем полную для расчёта процентов
        BigDecimal yearlyRateSub = (subsidizedRate != null && subsidizedRate.compareTo(BigDecimal.ZERO) > 0)
                ? subsidizedRate.divide(ONE_HUNDRED, MC)
                : yearlyRateFull;
        BigDecimal daysInYear = new BigDecimal("365");
        
        // Аннуитет по полной ставке
        BigDecimal aFull = calculateAnnuityPayment(principal, fullRate, durationMonths);
        
        // Платёж в льготный период: ручной ввод или расчёт по формуле
        BigDecimal aSub;
        boolean manualPayment = false;
        if (request.getSubsidizedPaymentAmount() != null && request.getSubsidizedPaymentAmount().compareTo(BigDecimal.ZERO) > 0) {
            // Используем введённый пользователем платёж
            aSub = request.getSubsidizedPaymentAmount().setScale(2, RoundingMode.HALF_UP);
            manualPayment = true;
        } else if (subsidizedRate != null && subsidizedRate.compareTo(BigDecimal.ZERO) > 0) {
            // Рассчитываем по льготной ставке
            aSub = calculateAnnuityPayment(principal, subsidizedRate, durationMonths);
        } else {
            // Если ничего не указано, используем полный аннуитет
            aSub = aFull;
        }
        
        log.debug("Субсидированная ипотека: платёж={} (ручной={}), полный аннуитет={}", aSub, manualPayment, aFull);
        
        BigDecimal balance = principal;
        BigDecimal totalPayment = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalSubsidy = BigDecimal.ZERO;
        BigDecimal balanceAfterSubsidy = null;
        int eventsPointer = 0;
        
        LocalDate previousDate = request.getDisbursementDate();
        LocalDate paymentDate = adjustForWeekend(request.resolveFirstPaymentDate(), adjustWeekends);
        
        for (int month = 1; month <= durationMonths && balance.compareTo(EPS) > 0; month++) {
            // Обработка досрочных платежей МЕЖДУ датами платежей (только BETWEEN_PAYMENTS)
            while (eventsPointer < events.size()) {
                EarlyPaymentEvent event = events.get(eventsPointer);
                boolean shouldApply = event.mode() == EarlyPaymentApplicationMode.BETWEEN_PAYMENTS 
                        && !event.date().isAfter(paymentDate);
                if (shouldApply) {
                    eventsPointer++;
                    BigDecimal deducted = event.amount().min(balance).setScale(2, RoundingMode.HALF_UP);
                    if (deducted.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    balance = balance.subtract(deducted);
                    totalPayment = totalPayment.add(deducted);
                    log.debug("Досрочный платёж (между): {} на {}, остаток: {}", deducted, event.date(), balance);
                    
                    // Добавляем строку досрочного платежа в график
                    PaymentScheduleItem earlyPaymentItem = PaymentScheduleItem.builder()
                            .monthNumber(0) // Без номера
                            .paymentDate(event.date())
                            .paymentAmount(deducted)
                            .principalPart(deducted)
                            .interestPart(BigDecimal.ZERO)
                            .remainingDebt(balance.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP))
                            .earlyPayment(true)
                            .build();
                    schedule.add(earlyPaymentItem);
                    
                    continue;
                }
                break;
            }
            // Расчёт количества дней в периоде
            long daysInPeriod = ChronoUnit.DAYS.between(previousDate, paymentDate);
            BigDecimal days = new BigDecimal(daysInPeriod);
            
            BigDecimal payment;
            BigDecimal interestClient; // Проценты, которые платит клиент
            BigDecimal principalPart;
            BigDecimal subsidy = BigDecimal.ZERO;
            
            if (month <= subsidyDuration) {
                // Период субсидии — проценты по льготной ставке ПО ДНЯМ
                BigDecimal interestByDays = balance.multiply(yearlyRateSub, MC)
                        .multiply(days, MC)
                        .divide(daysInYear, MC)
                        .setScale(2, RoundingMode.HALF_UP);
                
                // Реальные проценты по полной ставке (для расчёта субсидии)
                BigDecimal interestRealFull = balance.multiply(yearlyRateFull, MC)
                        .multiply(days, MC)
                        .divide(daysInYear, MC)
                        .setScale(2, RoundingMode.HALF_UP);
                
                if (subsidyMode == SubsidyMode.FIXED_PAYMENT) {
                    // Платёж = аннуитет по льготной ставке
                    payment = aSub;
                    
                    // Если проценты >= платежа, осн.долг = 0
                    if (interestByDays.compareTo(payment) >= 0) {
                        principalPart = BigDecimal.ZERO;
                        interestClient = payment; // Клиент платит весь платёж как проценты
                        // Субсидия = реальные проценты - то что заплатил клиент
                        subsidy = interestByDays.subtract(payment).max(BigDecimal.ZERO);
                    } else {
                        principalPart = payment.subtract(interestByDays);
                        interestClient = interestByDays;
                        subsidy = BigDecimal.ZERO; // Нет субсидии, если проценты < платежа
                    }
                    
                    // Дополнительная субсидия: разница между полной и льготной ставкой
                    BigDecimal rateSubsidy = interestRealFull.subtract(interestByDays).max(BigDecimal.ZERO);
                    subsidy = subsidy.add(rateSubsidy);
                } else {
                    // FLOATING_PAYMENT: основной долг как по полной ставке
                    principalPart = aFull.subtract(interestRealFull);
                    if (principalPart.compareTo(BigDecimal.ZERO) < 0) {
                        principalPart = BigDecimal.ZERO;
                    }
                    interestClient = interestByDays;
                    payment = principalPart.add(interestClient);
                    subsidy = interestRealFull.subtract(interestByDays).max(BigDecimal.ZERO);
                }
                
                // Сохраняем остаток после окончания субсидии
                if (month == subsidyDuration) {
                    balanceAfterSubsidy = balance.subtract(principalPart).setScale(2, RoundingMode.HALF_UP);
                }
            } else {
                // После субсидии — проценты по полной ставке ПО ДНЯМ
                BigDecimal interestByDays = balance.multiply(yearlyRateFull, MC)
                        .multiply(days, MC)
                        .divide(daysInYear, MC)
                        .setScale(2, RoundingMode.HALF_UP);
                
                payment = aFull;
                
                // Если проценты >= платежа, осн.долг = 0
                if (interestByDays.compareTo(payment) >= 0) {
                    principalPart = BigDecimal.ZERO;
                    interestClient = payment;
                } else {
                    principalPart = payment.subtract(interestByDays);
                    interestClient = interestByDays;
                }
            }
            
            // Ограничиваем основной долг остатком
            if (principalPart.compareTo(balance) > 0) {
                principalPart = balance;
                payment = principalPart.add(interestClient);
            }
            
            balance = balance.subtract(principalPart);
            
            PaymentScheduleItem item = PaymentScheduleItem.builder()
                    .monthNumber(month)
                    .paymentDate(paymentDate)
                    .paymentAmount(payment.setScale(2, RoundingMode.HALF_UP))
                    .principalPart(principalPart.setScale(2, RoundingMode.HALF_UP))
                    .interestPart(interestClient.setScale(2, RoundingMode.HALF_UP))
                    .remainingDebt(balance.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP))
                    .subsidyAmount(subsidy.setScale(2, RoundingMode.HALF_UP))
                    .build();
            schedule.add(item);
            
            totalPayment = totalPayment.add(payment);
            totalInterest = totalInterest.add(interestClient);
            totalSubsidy = totalSubsidy.add(subsidy);
            
            // Обработка досрочных платежей В ДАТУ платежа
            while (eventsPointer < events.size()) {
                EarlyPaymentEvent event = events.get(eventsPointer);
                if (event.date().isEqual(paymentDate)
                        && event.mode() == EarlyPaymentApplicationMode.ON_PAYMENT_DATE) {
                    eventsPointer++;
                    BigDecimal deducted = event.amount().min(balance).setScale(2, RoundingMode.HALF_UP);
                    if (deducted.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    balance = balance.subtract(deducted);
                    totalPayment = totalPayment.add(deducted);
                    log.debug("Досрочный платёж (в дату): {} на {}, остаток: {}", deducted, event.date(), balance);
                    
                    // Добавляем строку досрочного платежа в график
                    PaymentScheduleItem earlyPaymentItem = PaymentScheduleItem.builder()
                            .monthNumber(0) // Без номера
                            .paymentDate(event.date())
                            .paymentAmount(deducted)
                            .principalPart(deducted)
                            .interestPart(BigDecimal.ZERO)
                            .remainingDebt(balance.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP))
                            .earlyPayment(true)
                            .build();
                    schedule.add(earlyPaymentItem);
                    
                    continue;
                }
                break;
            }
            
            // Следующая дата платежа
            previousDate = paymentDate;
            LocalDate nextDate = request.getDisbursementDate().plusMonths(month + 1);
            paymentDate = adjustForWeekend(nextDate, adjustWeekends);
        }
        
        return new ScheduleResult(
                schedule,
                totalPayment.setScale(2, RoundingMode.HALF_UP),
                totalInterest.setScale(2, RoundingMode.HALF_UP),
                totalSubsidy.setScale(2, RoundingMode.HALF_UP),
                aSub.setScale(2, RoundingMode.HALF_UP), // subsidizedPayment
                aFull.setScale(2, RoundingMode.HALF_UP), // fullPayment
                balanceAfterSubsidy
        );
    }

    private List<EarlyPaymentEvent> expandEarlyPayments(LoanCalculationRequestDto request) {
        List<EarlyPaymentEvent> events = new ArrayList<>();
        log.debug("Единовременные досрочные платежи: {}", request.getEarlyPayments());
        log.debug("Периодические досрочные платежи: {}", request.getPeriodicEarlyPayments());
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
            LocalDate defaultEnd = request.resolveFirstPaymentDate().plusMonths(MAX_CALCULATION_MONTHS);
            for (PeriodicEarlyPaymentDto dto : request.getPeriodicEarlyPayments()) {
                if (dto.getStartDate() == null || dto.getIntervalMonths() == null || dto.getAmount() == null) {
                    continue;
                }
                // Используем endDate из DTO, если указан, иначе используем дефолтное значение
                LocalDate end = dto.getEndDate() != null ? dto.getEndDate() : defaultEnd;
                LocalDate date = dto.getStartDate();
                int guard = 0;
                // Генерируем события только до endDate (включительно)
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
                : request.resolveFirstPaymentDate();
        events.removeIf(event -> event.amount().compareTo(BigDecimal.ZERO) <= 0
                || (disbursementDate != null && event.date().isBefore(disbursementDate)));
        events.sort(Comparator.comparing(EarlyPaymentEvent::date));
        return events;
    }

    private List<RatePeriod> buildRateTimeline(LoanCalculationRequestDto request) {
        List<RatePeriod> ratePeriods = new ArrayList<>();
        ratePeriods.add(new RatePeriod(request.resolveFirstPaymentDate(), request.getInterestRate()));
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

    /**
     * Переносит дату с выходных на ближайший будний день (понедельник).
     */
    private LocalDate adjustForWeekend(LocalDate date, boolean adjustWeekends) {
        if (!adjustWeekends) {
            return date;
        }
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY) {
            return date.plusDays(2); // Перенос на понедельник
        } else if (day == DayOfWeek.SUNDAY) {
            return date.plusDays(1); // Перенос на понедельник
        }
        return date;
    }

    /**
     * Рассчитывает проценты за период.
     * Для стандартного аннуитета: остаток * (ставка / 100) / 12
     * Параметры дат оставлены для совместимости, но не используются в стандартном расчёте.
     */
    private BigDecimal calculateInterestForPeriod(BigDecimal remaining, BigDecimal annualRate,
                                                   LocalDate periodStart, LocalDate periodEnd) {
        // Стандартный расчёт: помесячно
        return remaining.multiply(annualRate, MC)
                .divide(ONE_HUNDRED, MC)
                .divide(TWELVE, MC)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Рассчитывает проценты за период по фактическому количеству дней.
     * Формула: остаток * (ставка / 100) * дней_в_периоде / дней_в_году
     */
    private BigDecimal calculateInterestForPeriodByDays(BigDecimal remaining, BigDecimal annualRate,
                                                         LocalDate periodStart, LocalDate periodEnd) {
        long daysInPeriod = ChronoUnit.DAYS.between(periodStart, periodEnd);
        int daysInYear = periodEnd.isLeapYear() ? 366 : 365;
        return remaining
                .multiply(annualRate, MC)
                .divide(ONE_HUNDRED, MC)
                .multiply(BigDecimal.valueOf(daysInPeriod), MC)
                .divide(BigDecimal.valueOf(daysInYear), MC)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void attachEarlyPayments(LoanCalculation calculation, LoanCalculationRequestDto request) {
        calculation.getEarlyPayments().clear();
        if (request.getEarlyPayments() != null) {
            for (EarlyPaymentDto dto : request.getEarlyPayments()) {
                if (dto.getPaymentDate() == null || dto.getAmount() == null) {
                    continue;
                }
                int monthNumber = (int) Math.max(1, ChronoUnit.MONTHS.between(
                        request.resolveFirstPaymentDate(), dto.getPaymentDate()) + 1);
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
                        .endDate(dto.getEndDate())
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

