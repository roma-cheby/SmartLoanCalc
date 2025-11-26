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

    @GetMapping({"/", "/calculator"})
    public String calculator(Model model,
                             @AuthenticationPrincipal UserDetails userDetails) {
        if (!model.containsAttribute("loanRequest")) {
            LoanCalculationRequestDto dto = new LoanCalculationRequestDto();
            dto.setDurationMonths(120);
            dto.setPrincipal(new BigDecimal("1000000"));
            dto.setInterestRate(new BigDecimal("10"));
            dto.setDisbursementDate(LocalDate.now());
            dto.setFirstPaymentDate(LocalDate.now().plusMonths(1));
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
        if (bindingResult.hasErrors()) {
            return "calculator";
        }
        String username = principal != null ? principal.getName() : null;
        LoanCalculationResponseDto response = calculationService.calculate(dto, username);
        model.addAttribute("result", response);
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
        redirectAttributes.addFlashAttribute("result", response);
        redirectAttributes.addFlashAttribute("loanRequestFromHistory", true);
        return "redirect:/calculator";
    }
}

