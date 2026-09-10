package com.smart.service;

import com.smart.entity.KbDocument;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface KBService {

    /**
     * 上传知识库文档：解析文本 → 分块（500 字符，重叠 50）→ 入库
     * 向量由 Python 算法服务在首次检索时自动补算
     */
    Map<String, Object> upload(MultipartFile file, Long uploaderId);

    /**
     * 文档列表
     */
    List<KbDocument> list();

    /**
     * 查看文档内容（按分块返回）
     */
    Map<String, Object> content(long id);

    /**
     * 获取文档记录
     */
    KbDocument get(long id);

    /**
     * 启用/停用文档
     */
    void updateStatus(long id, int status);

    /**
     * 删除文档及其分块
     */
    void delete(long id);
}
