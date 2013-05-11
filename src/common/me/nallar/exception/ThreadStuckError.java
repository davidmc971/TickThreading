package me.nallar.exception;

/**
 * Thrown via Thread.stop() to try to resolve a deadlock, should be caught in Thread.run(), and thread should attempt to resume working.
 */
public class ThreadStuckError extends Error {
	public ThreadStuckError() {
		super();
	}

	public ThreadStuckError(final String message) {
		super(message);
	}

	public ThreadStuckError(final String message, final Throwable cause) {
		super(message, cause);
	}

	public ThreadStuckError(final Throwable cause) {
		super(cause);
	}
}
