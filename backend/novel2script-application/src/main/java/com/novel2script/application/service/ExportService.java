package com.novel2script.application.service;

import com.novel2script.application.service.exporter.ExportOptions;
import com.novel2script.application.service.exporter.YamlExporter;
import com.novel2script.common.enums.ScriptStatus;
import com.novel2script.common.exception.BusinessException;
import com.novel2script.domain.model.Script;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for exporting scripts to various formats (YAML, etc.).
 *
 * <p>Delegates the actual serialization to {@link YamlExporter}. This class used
 * to return a hardcoded "# Script YAML placeholder" string and always report
 * schema validation as successful — both removed, since they produced fake
 * output that was indistinguishable from a real export.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final ScriptService scriptService;
    private final YamlExporter yamlExporter;

    /**
     * Export a script to YAML format.
     *
     * @throws BusinessException if the script does not exist or is not exportable
     */
    public String exportToYaml(Long scriptId) {
        Script script = scriptService.findById(scriptId)
                .orElseThrow(() -> new BusinessException("SCRIPT_NOT_FOUND",
                        "Script not found: id=" + scriptId));
        ensureExportable(script);
        log.info("Exporting script to YAML: scriptId={}, status={}, scenes={}",
                scriptId, script.getStatus(), script.getSceneCount());
        return yamlExporter.exportToString(script, ExportOptions.defaults());
    }

    /**
     * Validate a script against the YAML schema by running a real export with
     * schema validation enabled.
     *
     * @return true only when the script exists and produces schema-valid YAML
     */
    public boolean validateSchema(Long scriptId) {
        Script script = scriptService.findById(scriptId).orElse(null);
        if (script == null) {
            log.warn("validateSchema: script not found, id={}", scriptId);
            return false;
        }
        try {
            yamlExporter.exportToString(script, ExportOptions.defaults());
            return true;
        } catch (Exception e) {
            log.warn("validateSchema: script {} produced invalid YAML: {}", scriptId, e.getMessage());
            return false;
        }
    }

    /**
     * Ensure the script is ready for export (generation must have finished).
     * {@link ScriptStatus#COMPLETED_WITH_WARNINGS} counts as finished: gaps are
     * left empty on purpose instead of being filled with fabricated content.
     */
    public void ensureExportable(Script script) {
        if (script == null) {
            throw new BusinessException("SCRIPT_NOT_FOUND", "Script not found");
        }
        if (!script.isGenerated()) {
            throw new BusinessException("SCRIPT_NOT_COMPLETED",
                    "Script must be COMPLETED or COMPLETED_WITH_WARNINGS before export. Current: " + script.getStatus());
        }
    }
}
