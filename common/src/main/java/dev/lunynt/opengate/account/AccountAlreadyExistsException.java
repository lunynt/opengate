package dev.lunynt.opengate.account;

public final class AccountAlreadyExistsException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AccountAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
