package ru.Roman.NauJava.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.Roman.NauJava.dto.LoanCalculationFilterDto;
import ru.Roman.NauJava.dto.LoanCalculationHistoryDto;
import ru.Roman.NauJava.dto.LoanCalculationRequestDto;
import ru.Roman.NauJava.dto.LoanCalculationResponseDto;
import ru.Roman.NauJava.service.LoanCalculationService;

import java.util.List;

/**
 * REST API для внешних клиентов.
 */
@RestController
@RequestMapping("/api/v1/calculations")
@RequiredArgsConstructor
public class LoanCalculationRestController {

    private final LoanCalculationService calculationService;

    @GetMapping
    public List<LoanCalculationHistoryDto> list(LoanCalculationFilterDto filter,
                                                Authentication authentication) {
        return calculationService.getHistory(authentication.getName(), filter);
    }

    @GetMapping("/{id}")
    public LoanCalculationResponseDto get(@PathVariable Long id, Authentication authentication) {
        return calculationService.getCalculation(id, authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoanCalculationResponseDto create(@Valid @RequestBody LoanCalculationRequestDto request,
                                             Authentication authentication) {
        request.setSaveToHistory(true);
        return calculationService.calculate(request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        calculationService.delete(id, authentication.getName());
    }
}

