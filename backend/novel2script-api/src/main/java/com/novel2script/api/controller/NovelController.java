package com.novel2script.api.controller;

import com.novel2script.application.service.NovelService;
import com.novel2script.domain.model.Novel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for novel upload and management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/novels")
@RequiredArgsConstructor
@Tag(name = "Novel", description = "Novel upload and management APIs")
public class NovelController {

    private final NovelService novelService;

    /**
     * Auto-detect file encoding. Tries UTF-8 first, then common Chinese encodings.
     * Chinese novels from Chinese websites often use GBK/GB2312/GB18030 encoding.
     */
    private String decodeWithDetection(byte[] bytes, String fileName) {
        // Try UTF-8 first (most common for modern files)
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (isValidText(utf8)) {
            log.debug("File '{}' detected as UTF-8", fileName);
            return utf8;
        }

        // Try GB18030 (superset of GBK and GB2312) — common for Chinese novels
        try {
            String gbk = new String(bytes, Charset.forName("GB18030"));
            if (isLikelyChinese(gbk)) {
                log.info("File '{}' detected as GB18030/GBK (Chinese encoding), converted to UTF-8 internally", fileName);
                return gbk;
            }
        } catch (Exception ignored) {}

        // Try GBK explicitly
        try {
            String gbk = new String(bytes, Charset.forName("GBK"));
            if (isLikelyChinese(gbk)) {
                log.info("File '{}' detected as GBK (Chinese encoding), converted to UTF-8 internally", fileName);
                return gbk;
            }
        } catch (Exception ignored) {}

        // Fallback: return UTF-8 version (may have garbled chars) and warn
        log.warn("File '{}' encoding uncertain — defaulting to UTF-8. Content may be garbled if file uses non-UTF-8 encoding.", fileName);
        return utf8;
    }

    /** Check if text looks like valid UTF-8 decoded content */
    private boolean isValidText(String text) {
        if (text == null || text.isEmpty()) return false;
        // Check for replacement character (U+FFFD) which indicates decoding errors
        long replacementCount = text.chars().filter(c -> c == '�').count();
        if (replacementCount > text.length() * 0.01) return false; // >1% replacement chars = bad
        // Check for common garbled patterns
        int garbledCount = 0;
        for (int i = 0; i < Math.min(text.length(), 500); i++) {
            char c = text.charAt(i);
            // Unusual control chars (not common whitespace) indicate encoding issues
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') garbledCount++;
        }
        return garbledCount < 5;
    }

    /** Check if text looks like valid Chinese text (high proportion of CJK chars) */
    private boolean isLikelyChinese(String text) {
        if (text == null || text.isEmpty()) return false;
        int sampleLen = Math.min(text.length(), 500);
        int cjkCount = 0;
        for (int i = 0; i < sampleLen; i++) {
            char c = text.charAt(i);
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B) {
                cjkCount++;
            }
        }
        return cjkCount > sampleLen * 0.15; // at least 15% CJK characters
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload a novel file for processing")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "author", required = false) String author) throws Exception {

        log.info("Upload request: fileName='{}', size={} bytes", file.getOriginalFilename(), file.getSize());

        // 从文件名提取标题（如果没有提供）
        String novelTitle = (title != null && !title.isBlank()) ? title :
                file.getOriginalFilename() != null ?
                file.getOriginalFilename().replaceAll("\\.[^.]+$", "") : "未命名";

        String novelAuthor = (author != null && !author.isBlank()) ? author : "未知作者";

        // Auto-detect encoding and decode file content
        byte[] fileBytes = file.getBytes();
        String content = decodeWithDetection(fileBytes, file.getOriginalFilename());

        Novel novel = novelService.uploadNovel(
                novelTitle,
                novelAuthor,
                file.getOriginalFilename(),
                file.getSize(),
                content);

        // 使用 HashMap 避免 null 值问题
        Map<String, Object> response = new HashMap<>();
        response.put("novelId", novel.getId());
        response.put("title", novel.getTitle());
        response.put("chapterCount", novel.getChapterCount());
        response.put("totalChars", novel.getTotalChars());
        response.put("status", novel.getStatus() != null ? novel.getStatus().name() : "UNKNOWN");

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all novels")
    public ResponseEntity<List<Novel>> listAll() {
        List<Novel> novels = novelService.listAll();
        return ResponseEntity.ok(novels);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get novel by ID")
    public ResponseEntity<Novel> getById(@PathVariable Long id) {
        return novelService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a novel")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        novelService.deleteNovel(id);
        return ResponseEntity.noContent().build();
    }
}
