package com.novel2script.api.advice;

import com.novel2script.common.exception.AgentRetryException;
import com.novel2script.common.exception.BusinessException;
import com.novel2script.common.exception.ExportException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Global exception handler for the REST API.
 * Converts exceptions to RFC 7807 Problem Details.
 *
 * <p>SSE (text/event-stream) endpoints are deliberately excluded
 * from the general handler — their errors are handled inside
 * the controller's scheduled task to avoid response-type conflicts.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ───────────────────────────── SSE exclusion ─────────────────────────────

    /**
     * ClientAbortException — client disconnected from SSE stream.
     * This is normal lifecycle, not an application error.
     * Return {@code null} to let the connection die cleanly.
     */
    @ExceptionHandler(ClientAbortException.class)
    public Object handleClientAbort(ClientAbortException ex, HttpServletRequest request) {
        if (isSseRequest(request)) {
            log.debug("SSE client disconnected: {}", request.getRequestURI());
            return SseEmitter.event().comment("disconnected").data("").build(); // no-op sentinel
        }
        // Fall back to general handler for non-SSE ClientAbort
        return handleGeneral(ex);
    }

    /**
     * AsyncRequestNotUsableException — Spring's wrapper for broken async pipes.
     * Same treatment as ClientAbortException.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public Object handleAsyncNotUsable(AsyncRequestNotUsableException ex, HttpServletRequest request) {
        if (isSseRequest(request)) {
            log.debug("SSE async request not usable: {}", request.getRequestURI());
            return null;
        }
        return handleGeneral(ex);
    }

    /**
     * IOException at the controller level — often a broken SSE pipe.
     */
    @ExceptionHandler(IOException.class)
    public Object handleIOException(IOException ex, HttpServletRequest request) {
        if (isSseRequest(request)) {
            log.info("SSE I/O closed for client: {}", request.getRequestURI());
            return SseEmitter.event().comment("closed").data("").build();
        }
        log.warn("I/O exception on non-SSE endpoint: {}", ex.getMessage());
        return handleGeneral(ex);
    }

    // ──────────────────────── Business exceptions ────────────────────────────

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException ex) {
        log.warn("Business exception: [{}] {}", ex.getErrorCode(), ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Business Error");
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    @ExceptionHandler(AgentRetryException.class)
    public ProblemDetail handleAgentRetryException(AgentRetryException ex) {
        log.error("Agent retry exhausted: agent={}, attempts={}/{}",
                ex.getAgentName(), ex.getAttemptsMade(), ex.getMaxAttempts(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        problem.setTitle("AI Agent Error");
        problem.setProperty("agentName", ex.getAgentName());
        problem.setProperty("attemptsMade", ex.getAttemptsMade());
        problem.setProperty("maxAttempts", ex.getMaxAttempts());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    @ExceptionHandler(ExportException.class)
    public ProblemDetail handleExportException(ExportException ex) {
        log.warn("Export exception: [{}] {}", ex.getErrorCode(), ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Export Error");
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Argument");
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, errors);
        problem.setTitle("Validation Error");
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    // ──────────────────────────── Catch-all ──────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
        problem.setTitle("Internal Server Error");
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setType(URI.create("about:blank"));
        return problem;
    }

    // ────────────────────────────── helpers ──────────────────────────────────

    /**
     * Returns true if the current request produces {@code text/event-stream}.
     */
    private boolean isSseRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Path-based check: any endpoint with "progress" in the path is SSE
        if (path != null && path.contains("progress")) {
            return true;
        }
        // Accept-header check
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }
}
