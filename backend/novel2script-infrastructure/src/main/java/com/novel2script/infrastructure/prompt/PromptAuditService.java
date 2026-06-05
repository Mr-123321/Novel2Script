package com.novel2script.infrastructure.prompt;

import com.novel2script.domain.prompt.PromptAuditRecord;
import com.novel2script.domain.prompt.PromptStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for persisting AI call audit records and generating statistics.
 *
 * <p>Writes to the {@code prompt_audits} table (created by Flyway V2 migration).
 */
@Slf4j
@Service
public class PromptAuditService {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = """
            INSERT INTO prompt_audits
                (prompt_name, prompt_version, model_name,
                 input_tokens, output_tokens, latency_ms,
                 retry_count, is_success, error_message,
                 full_prompt, full_response, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String STATS_SQL = """
            SELECT
                prompt_name,
                COUNT(*)                     AS total_calls,
                SUM(CASE WHEN is_success = 1 THEN 1 ELSE 0 END) AS success_count,
                SUM(CASE WHEN is_success = 0 THEN 1 ELSE 0 END) AS failure_count,
                AVG(latency_ms)              AS avg_latency_ms,
                AVG(input_tokens)            AS avg_input_tokens,
                AVG(output_tokens)           AS avg_output_tokens,
                SUM(input_tokens + output_tokens) AS total_tokens
            FROM prompt_audits
            WHERE prompt_name = ?
              AND created_at BETWEEN ? AND ?
            GROUP BY prompt_name
            """;

    public PromptAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Persist an audit record to the database.
     * This is a fire-and-forget operation — failures are logged but never thrown.
     */
    public void record(PromptAuditRecord record) {
        try {
            jdbcTemplate.update(INSERT_SQL,
                    record.getPromptName(),
                    record.getPromptVersion(),
                    record.getModelName(),
                    record.getInputTokens(),
                    record.getOutputTokens(),
                    record.getLatencyMs(),
                    record.getRetryCount(),
                    record.isSuccess() ? 1 : 0,
                    truncate(record.getErrorMessage(), 500),
                    truncate(record.getFullPrompt(), 10000),
                    truncate(record.getFullResponse(), 10000),
                    record.getCreatedAt() != null
                            ? Timestamp.valueOf(record.getCreatedAt())
                            : Timestamp.valueOf(LocalDateTime.now()));
        } catch (Exception e) {
            log.error("Failed to write PromptAudit record for '{}': {}", record.getPromptName(), e.getMessage());
        }
    }

    /**
     * Get aggregated statistics for a prompt template within a time window.
     */
    public PromptStats getStats(String promptName, LocalDateTime from, LocalDateTime to) {
        List<PromptStats> results = jdbcTemplate.query(STATS_SQL,
                statsRowMapper(), promptName, Timestamp.valueOf(from), Timestamp.valueOf(to));

        if (results.isEmpty()) {
            return PromptStats.builder()
                    .promptName(promptName)
                    .totalCalls(0)
                    .successCount(0)
                    .failureCount(0)
                    .build();
        }
        return results.get(0);
    }

    /**
     * Get recent audit records for a prompt template.
     */
    public List<PromptAuditRecord> getRecent(String promptName, int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM prompt_audits WHERE prompt_name = ? ORDER BY created_at DESC LIMIT ?",
                auditRowMapper(), promptName, limit);
    }

    /**
     * Get recent audit records across all prompts.
     */
    public List<PromptAuditRecord> getRecentAll(int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM prompt_audits ORDER BY created_at DESC LIMIT ?",
                auditRowMapper(), limit);
    }

    // ── Row mappers ─────────────────────────────────────

    private RowMapper<PromptStats> statsRowMapper() {
        return (ResultSet rs, int rowNum) -> PromptStats.builder()
                .promptName(rs.getString("prompt_name"))
                .totalCalls(rs.getLong("total_calls"))
                .successCount(rs.getLong("success_count"))
                .failureCount(rs.getLong("failure_count"))
                .avgLatencyMs(rs.getDouble("avg_latency_ms"))
                .avgInputTokens(rs.getDouble("avg_input_tokens"))
                .avgOutputTokens(rs.getDouble("avg_output_tokens"))
                .totalTokens(rs.getLong("total_tokens"))
                .build();
    }

    private RowMapper<PromptAuditRecord> auditRowMapper() {
        return (ResultSet rs, int rowNum) -> PromptAuditRecord.builder()
                .id(rs.getLong("id"))
                .promptName(rs.getString("prompt_name"))
                .promptVersion(rs.getString("prompt_version"))
                .modelName(rs.getString("model_name"))
                .inputTokens(rs.getInt("input_tokens"))
                .outputTokens(rs.getInt("output_tokens"))
                .latencyMs(rs.getInt("latency_ms"))
                .retryCount(rs.getInt("retry_count"))
                .success(rs.getBoolean("is_success"))
                .errorMessage(rs.getString("error_message"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
