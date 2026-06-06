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
     *
     * <p>Detection strategy (scored heuristics):
     * <ol>
     *   <li>UTF-8 — check for replacement chars and control chars</li>
     *   <li>GB18030 (superset of GBK/GB2312) — check for high CJK ratio</li>
     *   <li>GBK — fallback for older files</li>
     *   <li>Windows-1252 → re-interpret as UTF-8 (fixes double-encoding)</li>
     * </ol>
     */
    private String decodeWithDetection(byte[] bytes, String fileName) {
        // ── Strategy 1: UTF-8 ──
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        EncodingScore utf8Score = scoreEncoding(utf8);
        if (utf8Score.isValid() && utf8Score.cjkRatio() > 0.05) {
            log.info("📄 '{}' → UTF-8 (score: {})", fileName, utf8Score);
            return utf8;
        }

        // ── Strategy 2: GB18030 (modern Chinese encoding, superset of GBK) ──
        try {
            String gb18030 = new String(bytes, Charset.forName("GB18030"));
            EncodingScore gbScore = scoreEncoding(gb18030);
            if (gbScore.isValid() && gbScore.cjkRatio() > 0.10) {
                log.info("📄 '{}' → GB18030 (score: {}, CJK: {:.1f}%)",
                        fileName, gbScore, gbScore.cjkRatio() * 100);
                return gb18030;
            }
        } catch (Exception ignored) {}

        // ── Strategy 3: GBK (older Chinese encoding) ──
        try {
            String gbk = new String(bytes, Charset.forName("GBK"));
            EncodingScore gbkScore = scoreEncoding(gbk);
            if (gbkScore.isValid() && gbkScore.cjkRatio() > 0.10) {
                log.info("📄 '{}' → GBK (score: {}, CJK: {:.1f}%)",
                        fileName, gbkScore, gbkScore.cjkRatio() * 100);
                return gbk;
            }
        } catch (Exception ignored) {}

        // ── Strategy 4: Double-encoding repair ──
        // If UTF-8 decoding looks "Latin-1 heavy", the file may be GBK bytes
        // that were incorrectly encoded as UTF-8 by an upstream tool.
        // Re-encode as Latin-1, then re-decode as GBK.
        if (utf8Score.cjkRatio() < 0.02
                && utf8Score.latinRatio() > 0.30
                && utf8Score.replacementCount() == 0) {
            try {
                // Re-interpret: take the garbled UTF-8 string, encode back to Latin-1 bytes,
                // then decode as GB18030
                byte[] reEncoded = utf8.getBytes(Charset.forName("ISO-8859-1"));
                String repaired = new String(reEncoded, Charset.forName("GB18030"));
                EncodingScore repairedScore = scoreEncoding(repaired);
                if (repairedScore.cjkRatio() > 0.10) {
                    log.warn("🔧 '{}' — detected double-encoding (UTF-8→Latin1→GB18030 repair). "
                            + "CJK ratio: {:.1f}%. Content has been repaired.",
                            fileName, repairedScore.cjkRatio() * 100);
                    return repaired;
                }
            } catch (Exception ignored) {}
        }

        // ── Fallback: return UTF-8 with warning ──
        if (utf8Score.cjkRatio() < 0.01) {
            log.warn("⚠️  '{}' — encoding detection failed. CJK ratio too low ({:.1f}%). "
                    + "Content may be garbled. Check that the file is a valid Chinese text file.",
                    fileName, utf8Score.cjkRatio() * 100);
        } else {
            log.info("📄 '{}' → UTF-8 (fallback, CJK: {:.1f}%)", fileName, utf8Score.cjkRatio() * 100);
        }
        return utf8;
    }

    /**
     * Score a decoded string for encoding quality.
     */
    private EncodingScore scoreEncoding(String text) {
        if (text == null || text.isEmpty()) {
            return new EncodingScore(0, 0, 0, 0, true);
        }

        int sampleLen = Math.min(text.length(), 2000);
        int cjkCount = 0;
        int latinCount = 0;
        int replacementCount = 0;
        int controlCharCount = 0;

        for (int i = 0; i < sampleLen; i++) {
            char c = text.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);

            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                cjkCount++;
            } else if (c == '�') {
                replacementCount++;
            } else if (c >= 0x00C0 && c <= 0x00FF) {
                latinCount++;
            } else if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') {
                controlCharCount++;
            }
        }

        double cjkRatio = (double) cjkCount / sampleLen;
        double latinRatio = (double) latinCount / sampleLen;
        boolean valid = replacementCount < sampleLen * 0.01   // <1% replacement chars
                && controlCharCount < 10;                       // few control chars

        return new EncodingScore(cjkRatio, latinRatio, replacementCount, controlCharCount, valid);
    }

    /** Immutable encoding quality score. */
    private record EncodingScore(
            double cjkRatio,
            double latinRatio,
            int replacementCount,
            int controlCharCount,
            boolean isValid
    ) {}

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
