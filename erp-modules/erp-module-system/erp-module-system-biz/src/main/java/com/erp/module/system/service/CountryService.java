package com.erp.module.system.service;

import com.erp.module.system.controller.vo.PaymentTermVOs.Country;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 国家/地区（01-14 4.2）：ISO 3166-1 二位代码 + 中英文名称，取自 JDK 内置的 CLDR 数据，不可维护。
 */
@Service
public class CountryService {

    private final List<Country> countries = Arrays.stream(Locale.getISOCountries())
            .map(code -> {
                Locale l = Locale.of("", code);
                return new Country(code, l.getDisplayCountry(Locale.SIMPLIFIED_CHINESE), l.getDisplayCountry(Locale.ENGLISH));
            })
            .sorted(Comparator.comparing(Country::code))
            .toList();

    public List<Country> list() {
        return countries;
    }
}
