package dev.lunynt.opengate.account;

public final class AccountAlreadyExistsException extends RuntimeException {
    public AccountAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
