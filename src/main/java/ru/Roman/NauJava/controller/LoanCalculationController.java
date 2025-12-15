package ru.Roman.NauJava.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.Roman.NauJava.domain.enums.EarlyPaymentApplicationMode;
import ru.Roman.NauJava.domain.enums.LoanCurrency;
import ru.Roman.NauJava.domain.enums.PaymentType;
import ru.Roman.NauJava.domain.enums.RecalculationMode;
import ru.Roman.NauJava.domain.enums.SubsidyMode;
import ru.Roman.NauJava.dto.LoanCalculationFilterDto;
import ru.Roman.NauJava.dto.LoanCalculationRequestDto;
import ru.Roman.NauJava.dto.LoanCalculationResponseDto;
import ru.Roman.NauJava.service.LoanCalculationService;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;

/**
 * MVC контроллер кредитного калькулятора.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class LoanCalculationController {

    private final LoanCalculationService calculationService;

    @ModelAttribute("currencies")
    public LoanCurrency[] currencies() {
        return LoanCurrency.values();
    }

    @ModelAttribute("paymentTypes")
    public PaymentType[] paymentTypes() {
        return PaymentType.values();
    }

    @ModelAttribute("recalculationModes")
    public RecalculationMode[] recalculationModes() {
        return RecalculationMode.values();
    }

    @ModelAttribute("applicationModes")
    public EarlyPaymentApplicationMode[] applicationModes() {
        return EarlyPaymentApplicationMode.values();
    }

    @ModelAttribute("subsidyModes")
    public SubsidyMode[] subsidyModes() {
        return SubsidyMode.values();
    }

    @GetMapping({"/", "/calculator"})
    public String calculator(Model model,
                             @AuthenticationPrincipal UserDetails userDetails) {
        if (!model.containsAttribute("loanRequest")) {
            LoanCalculationRequestDto dto = new LoanCalculationRequestDto();
            dto.setDurationMonths(120);
            dto.setPrincipal(new BigDecimal("1000000"));
            dto.setInterestRate(new BigDecimal("10"));
            dto.setDisbursementDate(LocalDate.now());
            model.addAttribute("loanRequest", dto);
        }
        model.addAttribute("authenticated", userDetails != null);
        return "calculator";
    }

    @PostMapping("/calculator")
    public String calculate(@Valid @ModelAttribute("loanRequest") LoanCalculationRequestDto dto,
                            BindingResult bindingResult,
                            Principal principal,
                            Model model) {
        model.addAttribute("authenticated", principal != null);
        model.addAttribute("loanRequest", dto); // Сохраняем данные формы при ошибках
        if (bindingResult.hasErrors()) {
            log.warn("Ошибки валидации: {}", bindingResult.getAllErrors());
            return "calculator";
        }
        try {
            String username = principal != null ? principal.getName() : null;
            log.debug("Начало расчёта для пользователя: {}", username);
            LoanCalculationResponseDto response = calculationService.calculate(dto, username);
            model.addAttribute("result", response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Ошибка при расчёте: {}", e.getMessage(), e);
            model.addAttribute("error", e.getMessage());
        } catch (NullPointerException e) {
            log.error("NullPointerException при расчёте", e);
            model.addAttribute("error", "Ошибка при расчёте: не заполнены обязательные поля");
        } catch (Exception e) {
            log.error("Неожиданная ошибка при расчёте", e);
            model.addAttribute("error", "Произошла ошибка при расчёте: " + e.getMessage());
        }
        return "calculator";
    }

    @GetMapping("/history")
    public String history(@ModelAttribute("filter") LoanCalculationFilterDto filter,
                          Principal principal,
                          Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        if (filter == null) {
            filter = new LoanCalculationFilterDto();
            model.addAttribute("filter", filter);
        }
        model.addAttribute("history", calculationService.getHistory(principal.getName(), filter));
        return "history";
    }

    @PostMapping("/history/{id}/delete")
    public String delete(@PathVariable Long id,
                         Principal principal,
                         RedirectAttributes redirectAttributes) {
        calculationService.delete(id, principal.getName());
        redirectAttributes.addFlashAttribute("message", "Расчёт удалён");
        return "redirect:/history";
    }

    @PostMapping("/history/{id}/rerun")
    public String rerun(@PathVariable Long id,
                        Principal principal,
                        RedirectAttributes redirectAttributes) {
        LoanCalculationResponseDto response = calculationService.rerun(id, principal.getName());
        
        // Заполняем форму параметрами из расчёта
        LoanCalculationRequestDto request = new LoanCalculationRequestDto();
        request.setLoanType(response.getLoanType());
        request.setCurrency(response.getCurrency());
        request.setPrincipal(response.getPrincipal());
        request.setInterestRate(response.getInterestRate());
        request.setDurationMonths(response.getDurationMonths());
        request.setPaymentType(response.getPaymentType());
        request.setDisbursementDate(response.getDisbursementDate());
        request.setRecalculationMode(response.getRecalculationMode());
        request.setAdjustWeekends(response.isAdjustWeekends());
        request.setDeveloperSubsidy(response.isDeveloperSubsidy());
        request.setSubsidizedRate(response.getSubsidizedRate());
        request.setSubsidyDurationMonths(response.getSubsidyDurationMonths());
        request.setSubsidyMode(response.getSubsidyMode());
        
        redirectAttributes.addFlashAttribute("loanRequest", request);
        redirectAttributes.addFlashAttribute("result", response);
        redirectAttributes.addFlashAttribute("loanRequestFromHistory", true);
        return "redirect:/calculator";
    }
}

