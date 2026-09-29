package com.erp.module.shipping.service.document;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Commercial Invoice 英文大写金额，如 12,500.00 USD → SAY US DOLLARS TWELVE THOUSAND FIVE HUNDRED ONLY（SHP-DOC-T02） */
public final class AmountInWords {

    private static final String[] ONES = {"", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN", "ELEVEN", "TWELVE",
            "THIRTEEN", "FOURTEEN", "FIFTEEN", "SIXTEEN", "SEVENTEEN", "EIGHTEEN", "NINETEEN"};
    private static final String[] TENS = {"", "", "TWENTY", "THIRTY", "FORTY", "FIFTY", "SIXTY", "SEVENTY", "EIGHTY", "NINETY"};
    private static final String[] SCALES = {"", " THOUSAND", " MILLION", " BILLION", " TRILLION"};
    private static final Map<String, String> CURRENCIES = Map.of("USD", "US DOLLARS", "EUR", "EUROS", "CNY", "CHINESE YUAN", "RMB", "CHINESE YUAN",
            "HKD", "HONG KONG DOLLARS", "GBP", "POUNDS STERLING", "JPY", "JAPANESE YEN", "AUD", "AUSTRALIAN DOLLARS", "CAD", "CANADIAN DOLLARS",
            "SGD", "SINGAPORE DOLLARS");

    private AmountInWords() {
    }

    public static String of(BigDecimal amount, String currency) {
        BigDecimal v = (amount == null ? BigDecimal.ZERO : amount.abs()).setScale(2, RoundingMode.HALF_UP);
        long whole = v.longValue();
        int cents = v.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        StringBuilder sb = new StringBuilder("SAY ");
        sb.append(CURRENCIES.getOrDefault(currency == null ? "" : currency.toUpperCase(), currency == null ? "" : currency.toUpperCase())).append(' ');
        sb.append(whole == 0 ? "ZERO" : words(whole));
        if (cents > 0) sb.append(" AND CENTS ").append(words(cents));
        return sb.append(" ONLY").toString();
    }

    static String words(long n) {
        StringBuilder sb = new StringBuilder();
        int scale = 0;
        while (n > 0) {
            int chunk = (int) (n % 1000);
            if (chunk > 0) {
                String w = hundreds(chunk) + SCALES[scale];
                sb.insert(0, sb.isEmpty() ? w : w + " ");
            }
            n /= 1000;
            scale++;
        }
        return sb.toString().trim();
    }

    private static String hundreds(int n) {
        StringBuilder sb = new StringBuilder();
        if (n >= 100) {
            sb.append(ONES[n / 100]).append(" HUNDRED");
            n %= 100;
            if (n > 0) sb.append(" AND ");
        }
        if (n >= 20) {
            sb.append(TENS[n / 10]);
            if (n % 10 > 0) sb.append('-').append(ONES[n % 10]);
        } else if (n > 0) {
            sb.append(ONES[n]);
        }
        return sb.toString();
    }
}
