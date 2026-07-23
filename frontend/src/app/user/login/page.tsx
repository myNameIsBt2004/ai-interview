"use client";

import { Button, Card, Form, Input, Tabs, message } from "antd";
import React, { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { userLoginUsingPost, userRegisterUsingPost } from "@/api/userController";

function AuthForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [loading, setLoading] = useState(false);

  const afterLogin = () => {
    const redirect = searchParams.get("redirect");
    router.push(redirect || "/mockInterview/add");
  };

  const onLogin = async (values: API.UserLoginRequest) => {
    setLoading(true);
    try {
      await userLoginUsingPost(values);
      message.success("登录成功");
      afterLogin();
    } catch (e: any) {
      message.error(e.message || "登录失败");
    } finally {
      setLoading(false);
    }
  };

  const onRegister = async (values: API.UserRegisterRequest) => {
    setLoading(true);
    try {
      await userRegisterUsingPost(values);
      message.success("注册成功，请登录");
    } catch (e: any) {
      message.error(e.message || "注册失败");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Tabs
      items={[
        {
          key: "login",
          label: "登录",
          children: (
            <Form layout="vertical" onFinish={onLogin} initialValues={{ userAccount: "admin" }}>
              <Form.Item
                label="账号"
                name="userAccount"
                rules={[{ required: true, message: "请输入账号" }]}
              >
                <Input placeholder="默认 admin" />
              </Form.Item>
              <Form.Item
                label="密码"
                name="userPassword"
                rules={[{ required: true, message: "请输入密码" }]}
              >
                <Input.Password placeholder="默认 12345678" />
              </Form.Item>
              <Button type="primary" htmlType="submit" block loading={loading}>
                登录
              </Button>
            </Form>
          ),
        },
        {
          key: "register",
          label: "注册",
          children: (
            <Form layout="vertical" onFinish={onRegister}>
              <Form.Item
                label="账号"
                name="userAccount"
                rules={[{ required: true, min: 4, message: "账号至少 4 位" }]}
              >
                <Input />
              </Form.Item>
              <Form.Item
                label="密码"
                name="userPassword"
                rules={[{ required: true, min: 8, message: "密码至少 8 位" }]}
              >
                <Input.Password />
              </Form.Item>
              <Form.Item
                label="确认密码"
                name="checkPassword"
                rules={[{ required: true, min: 8, message: "请再次输入密码" }]}
              >
                <Input.Password />
              </Form.Item>
              <Button type="primary" htmlType="submit" block loading={loading}>
                注册
              </Button>
            </Form>
          ),
        },
      ]}
    />
  );
}

export default function UserAuthPage() {
  return (
    <Card title="账号登录" style={{ maxWidth: 480, margin: "40px auto" }}>
      <Suspense fallback={<div>加载中...</div>}>
        <AuthForm />
      </Suspense>
    </Card>
  );
}
