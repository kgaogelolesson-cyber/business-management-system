package com.bms.util;

import com.bms.exception.ValidationException;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Turns what a person TYPED into numbers, or explains what is wrong.
 * Used by forms before the data goes to a service.
 */
public final class InputParser {

    private static final Pattern MONEY = Pattern.compile("^[0-9]{1,9}(\\.[0-9]{1,2})?$");
    private static final Pattern WHOLE_NUMBER = Pattern.compile("^[0-9]{1,9}$");

    private InputParser() {
    }

    /**
     * "120.50", "R120.50", "R 1 200.5" and "120" are accepted. The result is in cents (12050).
     * A comma is refused on purpose because it could mean thousands (1,200) or cents (120,50).
     */
    public static long parseMoneyToCents(String text, String currencySymbol) throws ValidationException {
        String t = (text == null) ? "" : text.replace(' ', ' ').trim();
        if (currencySymbol != null && !currencySymbol.isEmpty()
                && t.regionMatches(true, 0, currencySymbol, 0, currencySymbol.length())) {
            t = t.substring(currencySymbol.length());
        }
        t = t.replace(" ", "");

        if (t.isEmpty()) {
            throw new ValidationException("The price is required.");
        }
        if (t.contains(",")) {
            throw new ValidationException("Type the price with a dot and no commas, for example 1200.50.");
        }
        if (!MONEY.matcher(t).matches()) {
            throw new ValidationException(
                    "The price is not valid. Use numbers only, with at most 2 decimals (for example 129.99).");
        }
        return new BigDecimal(t).movePointRight(2).longValueExact();
    }

    /** A whole number such as 25. fieldName is used in the message ("Stock quantity"). */
    public static int parseWholeNumber(String text, String fieldName) throws ValidationException {
        String t = (text == null) ? "" : text.trim();
        if (t.isEmpty()) {
            throw new ValidationException(fieldName + " is required.");
        }
        if (!WHOLE_NUMBER.matcher(t).matches()) {
            throw new ValidationException(fieldName + " must be a whole number, 0 or more (for example 25).");
        }
        return Integer.parseInt(t);
    }
}
