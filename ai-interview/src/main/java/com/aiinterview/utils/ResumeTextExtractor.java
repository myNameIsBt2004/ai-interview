package com.aiinterview.utils;

import cn.hutool.core.util.StrUtil;
import com.aiinterview.common.ErrorCode;
import com.aiinterview.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 从上传的简历文件中提取纯文本
 */
public final class ResumeTextExtractor {

    private static final int MAX_CHARS = 30000;

    private ResumeTextExtractor() {
    }

    public static String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请上传简历文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = name.toLowerCase(Locale.ROOT);
        try {
            String text;
            if (lower.endsWith(".pdf")) {
                text = extractPdf(file.getBytes());
            } else if (lower.endsWith(".docx")) {
                text = extractDocx(file.getInputStream());
            } else if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv")
                    || (file.getContentType() != null && file.getContentType().startsWith("text/"))) {
                text = new String(file.getBytes(), StandardCharsets.UTF_8);
            } else if (lower.endsWith(".doc")) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "暂不支持旧版 .doc，请转成 PDF/DOCX/TXT");
            } else {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 PDF、DOCX、TXT、MD");
            }
            text = normalize(text);
            if (StrUtil.isBlank(text)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "未能从文件中识别到文字，请手动填写");
            }
            if (text.length() > MAX_CHARS) {
                return text.substring(0, MAX_CHARS);
            }
            return text;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "简历解析失败：" + e.getMessage());
        }
    }

    private static String extractPdf(byte[] bytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private static String extractDocx(InputStream inputStream) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    zis.transferTo(baos);
                    String xml = baos.toString(StandardCharsets.UTF_8);
                    return xml.replaceAll("<w:tab[^/]*/>", "\t")
                            .replaceAll("</w:p>", "\n")
                            .replaceAll("<[^>]+>", " ")
                            .replace("&amp;", "&")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&quot;", "\"")
                            .replace("&apos;", "'");
                }
            }
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "DOCX 内容为空或格式异常");
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u0000', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
