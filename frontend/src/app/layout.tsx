"use client";

import React from "react";
import { AntdRegistry } from "@ant-design/nextjs-registry";
import { ConfigProvider, Layout, Menu } from "antd";
import Link from "next/link";
import { usePathname } from "next/navigation";
import "./globals.css";

const { Header, Content } = Layout;

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const pathname = usePathname();
  const hideNav = pathname?.startsWith("/interview/room");

  return (
    <html lang="zh-CN">
      <body>
        <AntdRegistry>
          <ConfigProvider
            theme={{
              token: {
                colorPrimary: "#f5c518",
                borderRadius: 8,
                fontFamily: '"PingFang SC", "Microsoft YaHei", sans-serif',
              },
            }}
          >
            <Layout style={{ minHeight: "100vh", background: "var(--bg)" }}>
              {!hideNav && (
                <Header
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: 28,
                    background: "#fff",
                    borderBottom: "1px solid var(--border)",
                    padding: "0 28px",
                    height: 56,
                    lineHeight: "56px",
                    position: "sticky",
                    top: 0,
                    zIndex: 100,
                  }}
                >
                  <Link href="/" style={{ fontWeight: 700, fontSize: 18, whiteSpace: "nowrap" }}>
                    <span style={{ color: "var(--brand)" }}>AI</span> 模拟面试
                  </Link>
                  <Menu
                    mode="horizontal"
                    selectedKeys={[
                      pathname.startsWith("/interview/setup")
                        ? "/interview/setup"
                        : pathname.startsWith("/interview/records") ||
                            pathname.startsWith("/interview/report")
                          ? "/interview/records"
                          : pathname,
                    ]}
                    style={{ flex: 1, minWidth: 0, border: "none" }}
                    items={[
                      {
                        key: "/interview/setup",
                        label: <Link href="/interview/setup">开始面试</Link>,
                      },
                      {
                        key: "/interview/records",
                        label: <Link href="/interview/records">我的记录</Link>,
                      },
                      {
                        key: "/user/login",
                        label: <Link href="/user/login">登录</Link>,
                      },
                    ]}
                  />
                </Header>
              )}
              <Content>{children}</Content>
            </Layout>
          </ConfigProvider>
        </AntdRegistry>
      </body>
    </html>
  );
}
