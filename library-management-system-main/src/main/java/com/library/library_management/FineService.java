package com.library.library_management;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the configurable daily fine rate (stored in the database, so it survives restarts). */
@Service
public class FineService {

    static final String RATE_KEY = "fine.perDay";
    static final BigDecimal MAX_RATE = new BigDecimal("10000");

    private final AppSettingRepository settings;
    private final ZoneId zone;

    public FineService(AppSettingRepository settings,
                       @Value("${library.timezone:Asia/Kolkata}") String timezone) {
        this.settings = settings;
        this.zone = ZoneId.of(timezone);
    }

    public ZoneId zone() {
        return zone;
    }

    @Transactional(readOnly = true)
    public BigDecimal currentRate() {
        return settings.findById(RATE_KEY)
                .map(s -> new BigDecimal(s.getSettingValue()))
                .orElse(FineCalculator.DEFAULT_RATE);
    }

    @Transactional
    public BigDecimal setRate(BigDecimal requested) {
        if (requested == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the fine per day.");
        }
        if (requested.signum() < 0 || requested.compareTo(MAX_RATE) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Fine per day must be between 0 and 10000.");
        }
        if (requested.stripTrailingZeros().scale() > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Fine per day can have at most 2 decimal places.");
        }
        BigDecimal rate = requested.setScale(2, RoundingMode.HALF_UP);
        AppSetting s = settings.findById(RATE_KEY).orElseGet(() -> new AppSetting(RATE_KEY, rate.toPlainString()));
        s.setSettingValue(rate.toPlainString());
        settings.save(s);
        return rate;
    }
}
