package com.aiinterview.service;

import com.aiinterview.model.vo.ResumeParseVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 简历解析
 */
public interface ResumeService {

    ResumeParseVO parseResume(MultipartFile file);
}
