package com.kopite.devspace.auth.application;

public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException() {
        super("The user account is disabled");
    }
}
