package com.novel2script.common.exception;

/**
 * Thrown when an AI agent step fails and all retries are exhausted.
 */
public class AgentRetryException extends RuntimeException {

    private final String agentName;
    private final int attemptsMade;
    private final int maxAttempts;

    public AgentRetryException(String agentName, int attemptsMade, int maxAttempts, String message) {
        super(message);
        this.agentName = agentName;
        this.attemptsMade = attemptsMade;
        this.maxAttempts = maxAttempts;
    }

    public AgentRetryException(String agentName, int attemptsMade, int maxAttempts, String message, Throwable cause) {
        super(message, cause);
        this.agentName = agentName;
        this.attemptsMade = attemptsMade;
        this.maxAttempts = maxAttempts;
    }

    public String getAgentName() {
        return agentName;
    }

    public int getAttemptsMade() {
        return attemptsMade;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
