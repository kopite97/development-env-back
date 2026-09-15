package com.kopite.devspace.projectcategory.domain;

import java.text.Normalizer;

public record CategoryName(String value) {
    public CategoryName {
        if (value == null) throw invalid();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i == value.length() || !Character.isLowSurrogate(value.charAt(i))) throw invalid();
            } else if (Character.isLowSurrogate(c)) throw invalid();
        }
        value = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
        if (value.isBlank() || value.length() > 100 || value.indexOf('\0') >= 0) throw invalid();
    }
    private static CategoryValidationException invalid() {
        return new CategoryValidationException("name", "must be nonblank valid Unicode, at most 100 UTF-16 code units after trim and NFC");
    }
}
