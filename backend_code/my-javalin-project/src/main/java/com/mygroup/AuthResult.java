package com.mygroup;

public class AuthResult {
    public final boolean success;
    public final String message;
    public final String loggedPhone;

    //Constructor to initialize the attributes
    private AuthResult(boolean success, String message, String loggedPhone) {
        this.success = success;
        this.message = message;
        this.loggedPhone = loggedPhone;
    }

    //Result upon OK authentication
    public static AuthResult success(String message, String loggedPhone) {
        return new AuthResult(true, message, loggedPhone);
    }

    //Result upon FAIL authentication
    public static AuthResult failure(String message) {
        return new AuthResult(false, message, null);
    }
}
