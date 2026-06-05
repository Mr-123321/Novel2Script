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

        Novel novel = novelService.uploadNovel(
                novelTitle, 
                novelAuthor, 
                file.getOriginalFilename(),
                file.getSize(), 
                new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8));

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
