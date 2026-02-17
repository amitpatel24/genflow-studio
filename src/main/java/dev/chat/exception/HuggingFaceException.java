package dev.chat.exception;

/**
 * Custom exception for Hugging Face API errors.
 */
public class HuggingFaceException extends RuntimeException {
    
    private final int statusCode;
    private final String errorType;

    public HuggingFaceException(String message, int statusCode, String errorType) {
        super(message);
        this.statusCode = statusCode;
        this.errorType = errorType;
    }

    public HuggingFaceException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 500;
        this.errorType = "INTERNAL_ERROR";
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorType() {
        return errorType;
    }

    public static HuggingFaceException rateLimitExceeded() {
        return new HuggingFaceException(
            "Rate limit exceeded. Please wait a moment and try again.",
            429,
            "RATE_LIMIT"
        );
    }

    public static HuggingFaceException timeout() {
        return new HuggingFaceException(
            "The request timed out. The model might be loading, please try again.",
            504,
            "TIMEOUT"
        );
    }

    public static HuggingFaceException modelLoading() {
        return new HuggingFaceException(
            "The model is currently loading. Please wait a moment and try again.",
            503,
            "MODEL_LOADING"
        );
    }

    public static HuggingFaceException unauthorized() {
        return new HuggingFaceException(
            "Invalid API key. Please check your Hugging Face API key configuration.",
            401,
            "UNAUTHORIZED"
        );
    }
}
