package com.shopsphere.userservice.util;

import java.util.Locale;

public final class Emails {

    private Emails() {
    }

    /** Single normalization rule used on register AND login. */
    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
