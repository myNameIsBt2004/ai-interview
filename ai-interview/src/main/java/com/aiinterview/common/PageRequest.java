package com.aiinterview.common;

import com.aiinterview.constant.CommonConstant;
import lombok.Data;

/**
 * 分页请求
 */
@Data
public class PageRequest {

    private int current = 1;

    private int pageSize = 10;

    private String sortField;

    private String sortOrder = CommonConstant.SORT_ORDER_ASC;
}
