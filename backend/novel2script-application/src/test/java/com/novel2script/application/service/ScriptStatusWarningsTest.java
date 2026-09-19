package com.novel2script.application.service;

import com.novel2script.common.enums.ScriptStatus;
import com.novel2script.common.exception.BusinessException;
import com.novel2script.domain.model.Script;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W04 — "部分成功" (COMPLETED_WITH_WARNINGS) 状态语义测试。
 *
 * <p>Guards the terminal-status contract established after the mock layers were
 * removed: a run with failed scenes must be reported as
 * {@link ScriptStatus#COMPLETED_WITH_WARNINGS} (content kept, gaps flagged),
 * never silently as {@code COMPLETED} nor discarded as {@code FAILED}.
 */
@DisplayName("W04 — COMPLETED_WITH_WARNINGS 状态语义")
class ScriptStatusWarningsTest {

    @Nested
    @DisplayName("Script 领域状态流转")
    class DomainStatus {

        @Test
        @DisplayName("completeWithWarnings() → COMPLETED_WITH_WARNINGS 且进度 100")
        void completeWithWarnings_setsStatusAndProgress() {
            Script script = new Script();
            script.startGeneration();

            script.completeWithWarnings();

            assertThat(script.getStatus()).isEqualTo(ScriptStatus.COMPLETED_WITH_WARNINGS);
            assertThat(script.getProgress()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("complete() → COMPLETED 且进度 100")
        void complete_setsCompleted() {
            Script script = new Script();
            script.startGeneration();

            script.complete();

            assertThat(script.getStatus()).isEqualTo(ScriptStatus.COMPLETED);
            assertThat(script.getProgress()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("仅 updateProgress(100) 不得谎报 COMPLETED（回归护栏）")
        void updateProgress_doesNotImplyCompleted() {
            Script script = new Script();
            script.startGeneration();

            script.updateProgress(100.0);

            assertThat(script.getProgress()).isEqualTo(100.0);
            assertThat(script.getStatus())
                    .as("进度到 100 不等于生成成功，终态必须显式设置")
                    .isEqualTo(ScriptStatus.GENERATING);
        }

        @Test
        @DisplayName("COMPLETED 与 COMPLETED_WITH_WARNINGS 均视为已生成（可导出）")
        void isGenerated_acceptsBothTerminalSuccessStates() {
            Script completed = new Script();
            completed.complete();
            Script withWarnings = new Script();
            withWarnings.completeWithWarnings();

            assertThat(completed.isGenerated()).isTrue();
            assertThat(withWarnings.isGenerated()).isTrue();
        }

        @Test
        @DisplayName("GENERATING / DRAFT / FAILED 不算已生成")
        void isGenerated_rejectsNonTerminalStates() {
            Script generating = new Script();
            generating.startGeneration();
            Script draft = new Script();
            draft.setStatus(ScriptStatus.DRAFT);
            Script failed = new Script();
            failed.markFailed();

            assertThat(generating.isGenerated()).isFalse();
            assertThat(draft.isGenerated()).isFalse();
            assertThat(failed.isGenerated()).isFalse();
        }
    }

    @Nested
    @DisplayName("导出门禁（ExportService.ensureExportable）")
    class ExportGate {

        /** ensureExportable only inspects the passed script — deps are unused here. */
        private final ExportService exportService = new ExportService(null, null);

        @Test
        @DisplayName("COMPLETED_WITH_WARNINGS 允许导出")
        void allowsCompletedWithWarnings() {
            Script script = new Script();
            script.completeWithWarnings();

            assertThatCode(() -> exportService.ensureExportable(script)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("COMPLETED 允许导出")
        void allowsCompleted() {
            Script script = new Script();
            script.complete();

            assertThatCode(() -> exportService.ensureExportable(script)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("GENERATING 被拦截")
        void rejectsGenerating() {
            Script script = new Script();
            script.startGeneration();

            assertThatThrownBy(() -> exportService.ensureExportable(script))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("COMPLETED_WITH_WARNINGS");
        }

        @Test
        @DisplayName("脚本为 null 时抛出 SCRIPT_NOT_FOUND")
        void rejectsNullScript() {
            assertThatThrownBy(() -> exportService.ensureExportable(null))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
