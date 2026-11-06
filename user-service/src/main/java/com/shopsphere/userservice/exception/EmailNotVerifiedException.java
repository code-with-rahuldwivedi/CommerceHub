package com.shopsphere.userservice.exception;

public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Email address is not verified");
    }
}
