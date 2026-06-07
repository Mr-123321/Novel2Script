package com.novel2script.api.controller;

import com.novel2script.application.service.ScriptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for script export operations (YAML, etc.).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/exports")
@RequiredArgsConstructor
@Tag(name = "导出管理", description = "剧本导出 API（YAML 等格式）")
public class ExportController {

    private final ScriptService scriptService;

    @GetMapping("/{scriptId}/yaml")
    @Operation(summary = "获取剧本的 YAML 文本内容")
    public ResponseEntity<Map<String, Object>> getYaml(@PathVariable Long scriptId) {
        String yamlContent = scriptService.getYaml(scriptId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scriptId", scriptId);

        if (yamlContent != null && !yamlContent.isBlank()) {
            response.put("yaml", yamlContent);
            return ResponseEntity.ok(response);
        } else {
            response.put("yaml", null);
            response.put("message", "YAML 内容尚未生成或剧本不存在");
            return ResponseEntity.status(404).body(response);
        }
    }

    @GetMapping("/{scriptId}/yaml/download")
    @Operation(summary = "下载剧本的 YAML 文件")
    public ResponseEntity<Resource> downloadYaml(@PathVariable Long scriptId) {
        String yamlContent = scriptService.getYaml(scriptId);

        if (yamlContent == null || yamlContent.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        byte[] yamlBytes = yamlContent.getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(yamlBytes);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-yaml"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"script-" + scriptId + ".yaml\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(yamlBytes.length))
                .body(resource);
    }

    @GetMapping("/{scriptId}/txt/download")
    @Operation(summary = "下载剧本的 TXT 文本文件")
    public ResponseEntity<Resource> downloadTxt(@PathVariable Long scriptId) {
        String txtContent = scriptService.getTxt(scriptId);

        if (txtContent == null || txtContent.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        byte[] txtBytes = txtContent.getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(txtBytes);

        String title = scriptService.findById(scriptId)
                .map(s -> s.getTitle())
                .orElse("script");
        String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/plain; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeTitle + ".txt\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(txtBytes.length))
                .body(resource);
    }

    @GetMapping("/{scriptId}/md/download")
    @Operation(summary = "下载剧本的 Markdown 文件")
    public ResponseEntity<Resource> downloadMd(@PathVariable Long scriptId) {
        String mdContent = scriptService.getMd(scriptId);

        if (mdContent == null || mdContent.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        byte[] mdBytes = mdContent.getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(mdBytes);

        String title = scriptService.findById(scriptId)
                .map(s -> s.getTitle())
                .orElse("script");
        String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeTitle + ".md\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(mdBytes.length))
                .body(resource);
    }
}
