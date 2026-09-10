package com.smart.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.client.AlgoClient;
import com.smart.common.BizException;
import com.smart.entity.KbChunk;
import com.smart.entity.KbDocument;
import com.smart.mapper.KbChunkMapper;
import com.smart.mapper.KbDocumentMapper;
import com.smart.service.KBService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库服务：文档解析（txt/md/pdf/docx）→ 分块入库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KBServiceImpl implements KBService {

    /** 分块大小（字符）与重叠量，与 SRS FR-29 一致 */
    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final AlgoClient algoClient;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public Map<String, Object> upload(MultipartFile file, Long uploaderId) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.contains(".")) {
            throw new BizException("文件名不合法");
        }
        String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();

        // 1. 解析文本
        String text = switch (ext) {
            case "txt", "md" -> readPlain(file);
            case "pdf" -> readPdf(file);
            case "docx" -> readDocx(file);
            default -> throw new BizException("仅支持 txt / md / pdf / docx 格式文档");
        };
        text = text.replaceAll("\\s+", " ").trim();
        if (text.length() < 10) {
            throw new BizException("未能从文档中提取到有效内容（扫描版 PDF 请先 OCR）");
        }

        // 2. 保存原文件
        String storedPath = null;
        try {
            Path dir = Paths.get(uploadDir, "kb");
            Files.createDirectories(dir);
            storedPath = dir.resolve(IdUtil.simpleUUID() + "." + ext).toString();
            file.transferTo(new File(storedPath).getAbsoluteFile());
        } catch (IOException e) {
            log.error("知识库文件保存失败", e);
            throw new BizException("文件保存失败");
        }

        // 3. 文档入库
        KbDocument doc = new KbDocument();
        doc.setDocName(originalName);
        doc.setDocType(ext);
        doc.setFilePath(storedPath);
        doc.setStatus(1);
        doc.setUploaderId(uploaderId);
        documentMapper.insert(doc);

        // 4. 分块入库（vector 留空，Python 服务首次检索时自动补算并回写）
        List<String> chunks = chunkText(text);
        int idx = 1;
        for (String content : chunks) {
            KbChunk chunk = new KbChunk();
            chunk.setDocId(doc.getId());
            chunk.setChunkIndex(idx++);
            chunk.setContent(content);
            chunk.setTokenCount(content.length());
            chunkMapper.insert(chunk);
        }
        doc.setChunkCount(chunks.size());
        documentMapper.updateById(doc);

        log.info("知识库文档入库：{}，分块 {} 个", originalName, chunks.size());
        refreshAlgoCache();
        Map<String, Object> result = new HashMap<>();
        result.put("docId", doc.getId());
        result.put("docName", doc.getDocName());
        result.put("chunkCount", chunks.size());
        return result;
    }

    @Override
    public List<KbDocument> list() {
        return documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .orderByDesc(KbDocument::getUploadTime));
    }

    @Override
    public Map<String, Object> content(long id) {
        KbDocument doc = requireDoc(id);
        List<KbChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getDocId, id)
                .orderByAsc(KbChunk::getChunkIndex));
        Map<String, Object> result = new HashMap<>();
        result.put("docId", doc.getId());
        result.put("docName", doc.getDocName());
        result.put("docType", doc.getDocType());
        result.put("chunkCount", chunks.size());
        result.put("chunks", chunks.stream().map(c -> {
            Map<String, Object> item = new HashMap<>();
            item.put("chunkIndex", c.getChunkIndex());
            item.put("content", c.getContent());
            item.put("hasVector", c.getVector() != null && !c.getVector().isEmpty());
            return item;
        }).collect(java.util.stream.Collectors.toList()));
        return result;
    }

    @Override
    public KbDocument get(long id) {
        return requireDoc(id);
    }

    @Override
    public void updateStatus(long id, int status) {
        KbDocument doc = requireDoc(id);
        doc.setStatus(status == 1 ? 1 : 0);
        documentMapper.updateById(doc);
        refreshAlgoCache();
    }

    @Override
    public void delete(long id) {
        requireDoc(id);
        chunkMapper.delete(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocId, id));
        documentMapper.deleteById(id);
        refreshAlgoCache();
    }

    /**
     * 通知算法服务刷新知识库缓存（服务未启动时静默忽略，TTL 兜底）
     */
    private void refreshAlgoCache() {
        try {
            algoClient.post("/api/alg/kb/refresh", new HashMap<>());
        } catch (Exception e) {
            log.warn("知识库缓存刷新失败（算法服务未启动？）: {}", e.getMessage());
        }
    }

    // ===== 内部方法 =====

    private KbDocument requireDoc(long id) {
        KbDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BizException("文档不存在");
        }
        return doc;
    }

    /** 固定窗口分块：500 字符 / 块，块间重叠 50 字符 */
    private List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            chunks.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start = end - CHUNK_OVERLAP;
        }
        return chunks;
    }

    private String readPlain(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException("文件读取失败");
        }
    }

    private String readPdf(MultipartFile file) {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            log.error("PDF 解析失败", e);
            throw new BizException("PDF 解析失败（扫描件需先 OCR）");
        }
    }

    private String readDocx(MultipartFile file) {
        try (XWPFDocument document = new XWPFDocument(file.getInputStream());
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException e) {
            log.error("DOCX 解析失败", e);
            throw new BizException("Word 文档解析失败");
        }
    }
}
