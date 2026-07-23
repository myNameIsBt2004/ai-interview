package com.aiinterview.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aiinterview.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户服务
 */
public interface UserService extends IService<User> {

    long userRegister(String userAccount, String userPassword, String checkPassword);

    User userLogin(String userAccount, String userPassword, HttpServletRequest request);

    User getLoginUser(HttpServletRequest request);

    boolean isAdmin(HttpServletRequest request);

    boolean isAdmin(User user);

    boolean userLogout(HttpServletRequest request);

    User getLoginUserPermitNull(HttpServletRequest request);
}
