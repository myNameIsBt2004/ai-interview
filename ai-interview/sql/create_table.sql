-- =============================================================================
-- AI 模拟面试 · 完整初始化脚本（唯一需要执行的 SQL）
-- 适用：MySQL 8+
-- 效果：建库、建表、写入默认管理员
-- =============================================================================

CREATE DATABASE IF NOT EXISTS ai_interview
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE ai_interview;

-- ---------------------------------------------------------------------------
-- 用户表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin/ban',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    unique key uk_userAccount (userAccount)
) comment '用户' collate = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 模拟面试表（含岗位、简历、设置、对话、评估报告等全部字段）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mock_interview
(
    id                 bigint auto_increment comment 'id' primary key,
    -- 目标岗位
    workExperience     varchar(256)                       not null comment '工作年限（岗位要求）',
    jobPosition        varchar(256)                       not null comment '工作岗位',
    difficulty         varchar(50)                        not null comment '面试难度',
    interviewType      varchar(64)                        null comment '面试类型',
    salaryMin          int                                null comment '薪资下限(K)',
    salaryMax          int                                null comment '薪资上限(K)',
    jobDescription     varchar(1000)                      null comment '岗位描述',
    companyName        varchar(256)                       null comment '公司名称',
    -- 个人信息 / 简历
    personalDesc       text                               null comment '个人描述',
    yearsOfExperience  varchar(64)                        null comment '个人工作年限',
    coreSkills         varchar(1000)                      null comment '核心技能',
    projectExperience  mediumtext                         null comment '项目经验',
    resumeName         varchar(256)                       null comment '简历文件名',
    resumeText         mediumtext                         null comment '简历原文',
    -- 面试设置
    focus              varchar(64)                        null comment '面试重点',
    duration           int                                null comment '计划时长(分钟)',
    interviewer        varchar(128)                       null comment '面试官',
    -- 过程与结果
    score              int                                null comment '综合得分',
    durationMinutes    int                                null comment '实际时长(分钟)',
    reportJson         mediumtext                         null comment '结构化评估报告JSON',
    startTime          datetime                           null comment '面试开始时间',
    messages           mediumtext                         null comment '消息列表（JSON）',
    status             int      default 0                 not null comment '状态（0-待开始、1-进行中、2-已结束）',
    userId             bigint                             not null comment '创建人（用户 id）',
    createTime         datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime         datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete           tinyint  default 0                 not null comment '是否删除（逻辑删除）',
    index idx_userId (userId)
) comment '模拟面试' collate = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 默认管理员：账号 admin / 密码 12345678
-- 密码存储 = md5('aiinterview' + '12345678')
-- ---------------------------------------------------------------------------
INSERT INTO user (userAccount, userPassword, userName, userRole)
SELECT 'admin', 'fb90b4976fa7ab746aa3be3caf6cee64', '管理员', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM user WHERE userAccount = 'admin');
