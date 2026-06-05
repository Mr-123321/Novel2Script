package com.novel2script.application.service;

import com.novel2script.common.exception.BusinessException;
import com.novel2script.domain.model.Script;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for exporting scripts to various formats (YAML, etc.).
 */
@Slf4j
@Service
public class ExportService {

    /**
     * Export a script to YAML format.
     */
    public String exportToYaml(Long scriptId) {
        log.info("Exporting script to YAML: scriptId={}", scriptId);
        // TODO: fetch script, validate, convert to YAML
        return "# Script YAML placeholder\n";
    }

    /**
     * Validate a script against the YAML schema.
     */
    public boolean validateSchema(Long scriptId) {
        log.info("Validating script schema: scriptId={}", scriptId);
        // TODO: implement schema validation
        return true;
    }

    /**
     * Ensure the script is ready for export (must be COMPLETED).
     */
    public void ensureExportable(Script script) {
        if (script == null) {
            throw new BusinessException("SCRIPT_NOT_FOUND", "Script not found");
        }
        if (!script.isGenerated()) {
            throw new BusinessException("SCRIPT_NOT_COMPLETED",
                    "Script must be COMPLETED before export. Current: " + script.getStatus());
        }
    }
}
