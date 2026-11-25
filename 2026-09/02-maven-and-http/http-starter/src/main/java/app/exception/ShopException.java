package app.exception;

public class ShopException extends RuntimeException {

    public enum Kind {
        INVALID,
        MISSING,
        CONFLICT
    }

    private final Kind kind;

    public ShopException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}