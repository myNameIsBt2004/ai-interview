package com.aiinterview.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 模拟面试
 */
@TableName(value = "mock_interview")
@Data
public class MockInterview implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String workExperience;

    private String jobPosition;

    private String difficulty;

    /**
     * 消息列表（JSON）
     */
    private String messages;

    /**
     * 状态（0-待开始、1-进行中、2-已结束）
     */
    private Integer status;

    private Long userId;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
