package dev.chat.config;

import dev.chat.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HTTP interceptor for rate limiting API requests.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;

    public RateLimitInterceptor(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientId = getClientId(request);
        
        if (!rateLimiterService.isAllowed(clientId)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("""
                {"success": false, "error": "Rate limit exceeded. Please wait before making more requests."}
                """);
            return false;
        }
        
        // Add rate limit headers
        int remaining = rateLimiterService.getRemainingRequests(clientId);
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Limit", "30");
        
        return true;
    }

    private String getClientId(HttpServletRequest request) {
        // Try to get session ID first
        String sessionId = request.getParameter("sessionId");
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }
        
        // Fall back to IP address
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        
        return request.getRemoteAddr();
    }
}
