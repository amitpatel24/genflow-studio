package dev.chat.exception;

/**
 * Custom exception for AI API errors (Groq, Hugging Face, etc.).
 */
public class AiApiException extends RuntimeException {
    
    private final int statusCode;
    private final String errorType;

    public AiApiException(String message) {
        super(message);
        this.statusCode = 500;
        this.errorType = "UNKNOWN";
    }

    public AiApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 500;
        this.errorType = "UNKNOWN";
    }

    public AiApiException(String message, int statusCode, String errorType) {
        super(message);
        this.statusCode = statusCode;
        this.errorType = errorType;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorType() {
        return errorType;
    }

    public static AiApiException unauthorized() {
        return new AiApiException(
            "Invalid API key. Please check your Groq API key.",
            401,
            "UNAUTHORIZED"
        );
    }

    public static AiApiException rateLimitExceeded() {
        return new AiApiException(
            "Rate limit exceeded. Please wait a moment and try again.",
            429,
            "RATE_LIMIT"
        );
    }

    public static AiApiException serviceUnavailable() {
        return new AiApiException(
            "AI service is temporarily unavailable. Please try again.",
            503,
            "SERVICE_UNAVAILABLE"
        );
    }

    public static AiApiException modelLoading() {
        return new AiApiException(
            "Model is loading. Please wait a few seconds and try again.",
            503,
            "MODEL_LOADING"
        );
    }

    public static AiApiException timeout() {
        return new AiApiException(
            "Request timed out. The model is taking too long to respond.",
            504,
            "TIMEOUT"
        );
    }
}
