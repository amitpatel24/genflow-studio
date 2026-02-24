package dev.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe in-memory rate limiter for API protection.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    
    private static final int MAX_REQUESTS_PER_MINUTE = 30;
    private static final int MAX_REQUESTS_PER_HOUR = 200;
    
    private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    public static class RateLimitBucket {
        private final AtomicInteger minuteCount = new AtomicInteger(0);
        private final AtomicInteger hourCount = new AtomicInteger(0);
        private final AtomicReference<Instant> minuteStart = new AtomicReference<>(Instant.now());
        private final AtomicReference<Instant> hourStart = new AtomicReference<>(Instant.now());
        
        public synchronized boolean tryAcquire() {
            Instant now = Instant.now();
            
            // Reset minute window if needed
            if (now.isAfter(minuteStart.get().plusSeconds(60))) {
                minuteCount.set(0);
                minuteStart.set(now);
            }
            
            // Reset hour window if needed
            if (now.isAfter(hourStart.get().plusSeconds(3600))) {
                hourCount.set(0);
                hourStart.set(now);
            }
            
            // Check limits
            if (minuteCount.get() >= MAX_REQUESTS_PER_MINUTE) {
                return false;
            }
            if (hourCount.get() >= MAX_REQUESTS_PER_HOUR) {
                return false;
            }
            
            // Increment counters atomically
            minuteCount.incrementAndGet();
            hourCount.incrementAndGet();
            return true;
        }
        
        public int getRemainingMinute() {
            Instant now = Instant.now();
            if (now.isAfter(minuteStart.get().plusSeconds(60))) {
                return MAX_REQUESTS_PER_MINUTE;
            }
            return Math.max(0, MAX_REQUESTS_PER_MINUTE - minuteCount.get());
        }
    }

    public boolean isAllowed(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            clientId = "anonymous";
        }
        
        RateLimitBucket bucket = buckets.computeIfAbsent(clientId, k -> new RateLimitBucket());
        boolean allowed = bucket.tryAcquire();
        
        if (!allowed) {
            log.warn("Rate limit exceeded for client: {}", clientId);
        }
        
        return allowed;
    }

    public int getRemainingRequests(String clientId) {
        RateLimitBucket bucket = buckets.get(clientId);
        if (bucket == null) {
            return MAX_REQUESTS_PER_MINUTE;
        }
        return bucket.getRemainingMinute();
    }

    public void resetLimits(String clientId) {
        buckets.remove(clientId);
    }
    
    public void cleanupOldBuckets() {
        Instant cutoff = Instant.now().minusSeconds(7200); // 2 hours
        buckets.entrySet().removeIf(entry -> 
            entry.getValue().hourStart.get().isBefore(cutoff));
    }
}
