package com.novel2script.api.controller;

import com.novel2script.application.service.NovelService;
import com.novel2script.domain.dto.NovelUploadDTO;
import com.novel2script.domain.model.Novel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<Map<String, Object>> upload(@RequestBody NovelUploadDTO dto) {
        log.info("Upload request: title='{}', fileName='{}'", dto.title(), dto.fileName());

        Novel novel = novelService.uploadNovel(
                dto.title(), dto.author(), dto.fileName(),
                dto.fileSize(), dto.content());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "novelId", novel.getId(),
                "title", novel.getTitle(),
                "chapterCount", novel.getChapterCount(),
                "totalChars", novel.getTotalChars(),
                "status", novel.getStatus().name()
        ));
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
