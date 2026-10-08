package com.bms.util;

import java.math.BigDecimal;
import java.util.Locale;

/** Turns money stored as whole cents (68498) into text for the screen ("R684.98"). */
public final class MoneyFormatter {

    private MoneyFormatter() {
    }

    public static String format(long cents, String currencySymbol) {
        BigDecimal amount = BigDecimal.valueOf(cents, 2);   // 68498 cents -> 684.98
        return String.format(Locale.US, "%s%,.2f", currencySymbol, amount);
    }
}
