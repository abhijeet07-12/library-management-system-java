package com.library.library_management;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fines")
public class FineController {

    public record FineSettings(BigDecimal finePerDay) {
    }

    private final FineService fineService;

    public FineController(FineService fineService) {
        this.fineService = fineService;
    }

    /** GET /api/fines/settings -> { "finePerDay": 5.00 } */
    @GetMapping("/settings")
    public FineSettings get() {
        return new FineSettings(fineService.currentRate());
    }

    /** PUT /api/fines/settings  { "finePerDay": 7 } */
    @PutMapping("/settings")
    public FineSettings update(@RequestBody FineSettings request) {
        return new FineSettings(fineService.setRate(request == null ? null : request.finePerDay()));
    }
}
