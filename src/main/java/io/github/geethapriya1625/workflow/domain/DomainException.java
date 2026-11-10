package io.github.geethapriya1625.workflow.domain;

/** Business-rule violations. The web layer maps each subtype to one HTTP status. */
public abstract sealed class DomainException extends RuntimeException
        permits DomainException.NotFound, DomainException.NotAllowed, DomainException.InvalidState {

    protected DomainException(String message) {
        super(message);
    }

    /** 404 */
    public static final class NotFound extends DomainException {
        public NotFound(String message) {
            super(message);
        }
    }

    /** 403: the caller is authenticated but this action is not theirs to take. */
    public static final class NotAllowed extends DomainException {
        public NotAllowed(String message) {
            super(message);
        }
    }

    /** 409: the action is valid in general but not in the request's current state. */
    public static final class InvalidState extends DomainException {
        public InvalidState(String message) {
            super(message);
        }
    }
}
