package com.smart.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.smart.common.BizException;
import com.smart.common.Result;
import com.smart.entity.KbDocument;
import com.smart.service.KBService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理接口（AI 碳管家 RAG 数据源）
 */
@Tag(name = "知识库管理")
@RestController
@RequestMapping("/kb")
@RequiredArgsConstructor
public class KBController {

    private final KBService kbService;

    @Operation(summary = "上传知识库文档（txt/md/pdf/docx，管理员）")
    @SaCheckRole("ADMIN")
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(kbService.upload(file, StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "知识库文档列表")
    @GetMapping("/list")
    public Result<List<KbDocument>> list() {
        return Result.ok(kbService.list());
    }

    @Operation(summary = "查看文档内容（按分块返回）")
    @GetMapping("/{id}/content")
    public Result<Map<String, Object>> content(@PathVariable long id) {
        return Result.ok(kbService.content(id));
    }

    @Operation(summary = "查看文档原件（浏览器内嵌预览/下载）")
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable long id) {
        KbDocument doc = kbService.get(id);
        Path path = Paths.get(doc.getFilePath());
        if (!Files.exists(path)) {
            throw new BizException("原件文件不存在（可能已被清理）");
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            String fileName = URLEncoder.encode(doc.getDocName(), StandardCharsets.UTF_8).replace("+", "%20");
            MediaType mediaType = switch (doc.getDocType()) {
                case "pdf" -> MediaType.APPLICATION_PDF;
                case "docx" -> MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                default -> MediaType.TEXT_PLAIN;
            };
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + fileName)
                    .body(bytes);
        } catch (IOException e) {
            throw new BizException("文件读取失败");
        }
    }

    @Operation(summary = "启用/停用文档（管理员）")
    @SaCheckRole("ADMIN")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable long id, @RequestParam int status) {
        kbService.updateStatus(id, status);
        return Result.ok();
    }

    @Operation(summary = "删除文档（管理员）")
    @SaCheckRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        kbService.delete(id);
        return Result.ok();
    }
}
