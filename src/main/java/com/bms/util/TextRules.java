package com.bms.util;

import com.bms.exception.ValidationException;

/**
 * One shared rule for free-text fields: no control or "invisible" characters.
 * Why: a line break inside a name breaks tables, CSV exports and logs, and a NUL (character 0) can cut
 * text short in some programs. The invisible "right-to-left override" characters can make a name look
 * like something else on screen. None of these belong in a business name, so we refuse them.
 */
public final class TextRules {

    private TextRules() {
    }

    /** For one-line fields (names, categories, addresses...). Null is fine (the "required" rule is checked elsewhere). */
    public static void requireSingleLine(String value, String label) throws ValidationException {
        if (value != null && containsBadCharacter(value, false)) {
            throw new ValidationException("The " + label
                    + " contains characters that are not allowed (such as line breaks, tabs or hidden characters).");
        }
    }

    /** For multi-line fields (descriptions): line breaks and tabs are allowed, other control characters are not. */
    public static void requireText(String value, String label) throws ValidationException {
        if (value != null && containsBadCharacter(value, true)) {
            throw new ValidationException("The " + label + " contains characters that are not allowed (hidden control characters).");
        }
    }

    static boolean containsBadCharacter(String text, boolean allowLineBreaksAndTabs) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (allowLineBreaksAndTabs && (c == '\n' || c == '\r' || c == '\t')) {
                continue;
            }
            if (Character.isISOControl(c)                      // 0-31, 127-159 (includes NUL, newline, tab)
                    || (c >= '​' && c <= '‏')        // zero-width and direction marks
                    || (c >= '‪' && c <= '‮')        // direction embedding / override
                    || (c >= '⁠' && c <= '⁤')        // word joiner and invisible operators
                    || (c >= '⁦' && c <= '⁩')        // direction isolates
                    || c == '﻿') {                        // zero-width no-break space
                return true;
            }
        }
        return false;
    }
}
