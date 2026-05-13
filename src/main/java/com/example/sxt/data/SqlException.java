package com.example.sxt.data;

/**
 * Wraps SQL exceptions from DAO operations for uniform handling
 * by callers via {@link java.util.concurrent.CompletableFuture#exceptionally}.
 */
public class SqlException extends RuntimeException {

    public SqlException(String message) {
        super(message);
    }

    public SqlException(String message, Throwable cause) {
        super(message, cause);
    }
}
