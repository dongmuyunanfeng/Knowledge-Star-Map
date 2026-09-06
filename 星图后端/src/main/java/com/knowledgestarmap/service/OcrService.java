package com.knowledgestarmap.service;

public interface OcrService {

    /**
     * 识别图片中的文字。
     *
     * @param imageBytes 图片原始字节
     * @param mimeType   图片 MIME 类型（如 image/png）
     * @return 识别出的文字；识别失败返回 null
     */
    String recognize(byte[] imageBytes, String mimeType);
}
