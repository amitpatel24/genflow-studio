package dev.chat.service;

import dev.chat.dto.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory conversation storage for maintaining chat history per session.
 */
@Service
public class ConversationMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryService.class);
    private static final int MAX_HISTORY_SIZE = 20;
    private static final int MAX_SESSIONS = 1000;

    private final Map<String, ConversationSession> sessions = new ConcurrentHashMap<>();

    public record ConversationSession(
        String sessionId,
        List<ChatMessage> messages,
        Instant createdAt,
        Instant lastAccessedAt
    ) {
        public ConversationSession(String sessionId) {
            this(sessionId, Collections.synchronizedList(new ArrayList<>()), Instant.now(), Instant.now());
        }
    }

    public String createSession() {
        cleanupOldSessions();
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new ConversationSession(sessionId));
        log.debug("Created new session: {}", sessionId);
        return sessionId;
    }

    public void addMessage(String sessionId, ChatMessage message) {
        var session = sessions.computeIfAbsent(sessionId, ConversationSession::new);
        session.messages().add(message);
        
        // Trim old messages if exceeding max
        while (session.messages().size() > MAX_HISTORY_SIZE) {
            // Keep system message if present, remove oldest user/assistant pair
            if (session.messages().size() > 1 && 
                "system".equals(session.messages().get(0).role())) {
                session.messages().remove(1);
            } else {
                session.messages().remove(0);
            }
        }
        
        // Update last accessed time
        sessions.put(sessionId, new ConversationSession(
            session.sessionId(),
            session.messages(),
            session.createdAt(),
            Instant.now()
        ));
    }

    public void addUserMessage(String sessionId, String content) {
        addMessage(sessionId, ChatMessage.user(content));
    }

    public void addAssistantMessage(String sessionId, String content) {
        addMessage(sessionId, ChatMessage.assistant(content));
    }

    public List<ChatMessage> getHistory(String sessionId) {
        var session = sessions.get(sessionId);
        if (session == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(session.messages());
    }

    public List<ChatMessage> getHistoryWithSystemPrompt(String sessionId, String systemPrompt) {
        List<ChatMessage> history = new ArrayList<>();
        history.add(ChatMessage.system(systemPrompt));
        history.addAll(getHistory(sessionId));
        return history;
    }

    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
        log.debug("Cleared session: {}", sessionId);
    }

    public boolean sessionExists(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    public int getSessionCount() {
        return sessions.size();
    }

    private void cleanupOldSessions() {
        if (sessions.size() < MAX_SESSIONS) {
            return;
        }
        
        // Remove sessions older than 1 hour
        Instant cutoff = Instant.now().minusSeconds(3600);
        sessions.entrySet().removeIf(entry -> 
            entry.getValue().lastAccessedAt().isBefore(cutoff));
        
        log.info("Cleaned up old sessions, remaining: {}", sessions.size());
    }
}
