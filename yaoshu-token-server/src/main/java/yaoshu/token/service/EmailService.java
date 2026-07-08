package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.CommonConstants;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.Base64;

/**
 * 邮件发送服务  * <p>
 * 使用 Java 原生 Socket 实现 SMTP 协议发送（类似 Go net/smtp，零额外依赖）。
 */
@Slf4j
public final class EmailService {

    private EmailService() {
    }

    /**
     * 发送 HTML 邮件
     */
    public static boolean sendEmail(String subject, String receiver, String content) {
        String from = CommonConstants.smtpFrom;
        if (from.isEmpty()) {
            from = CommonConstants.smtpAccount;
        }
        if (CommonConstants.smtpServer.isEmpty() && CommonConstants.smtpAccount.isEmpty()) {
            SysLogService.sysError("SMTP 服务器未配置");
            return false;
        }

        try {
            String host = CommonConstants.smtpServer;
            int port = CommonConstants.smtpPort;

            // 判断是否需要 Outlook LOGIN 认证
            boolean useLoginAuth = CommonConstants.smtpForceAuthLogin
                    || OutlookAuthService.isOutlookServer(host)
                    || CommonConstants.emailLoginAuthServerList.contains(host);

            // 构造邮件内容（RFC 2047 Base64 编码，格式 =?UTF-8?B?<base64>?=）
            String encodedSubject = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=";
            // 发件人名称含非 ASCII 时需 RFC 2047 编码，否则部分邮件客户端解析邮件头异常
            String encodedFromName = encodeMailboxName(CommonConstants.systemName);
            StringBuilder mail = new StringBuilder();
            mail.append("To: ").append(receiver).append("\r\n");
            mail.append("From: ").append(encodedFromName).append(" <").append(from).append(">\r\n");
            mail.append("Subject: ").append(encodedSubject).append("\r\n");
            mail.append("Content-Type: text/html; charset=UTF-8\r\n");
            mail.append("\r\n");
            mail.append(content);
            mail.append("\r\n");

            if (port == 465 || CommonConstants.smtpSslEnabled) {
                sendWithSSL(host, port, from, receiver, useLoginAuth, mail.toString());
            } else {
                sendWithStartTLS(host, port, from, receiver, useLoginAuth, mail.toString());
            }
            return true;
        } catch (Exception e) {
            SysLogService.sysError("failed to send email to " + receiver + ": " + e.getMessage());
            return false;
        }
    }

    /** SSL 直连发送（端口 465） */
    private static void sendWithSSL(String host, int port, String from, String receiver,
                                     boolean useLoginAuth, String mailContent) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port)) {
            socket.startHandshake();
            smtpConversation(socket, host, from, receiver, useLoginAuth, mailContent);
        }
    }

    /** STARTTLS 发送（端口 587） */
    private static void sendWithStartTLS(String host, int port, String from, String receiver,
                                          boolean useLoginAuth, String mailContent) throws IOException {
        try (Socket socket = new Socket(host, port)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            readResponse(reader, 220);
            sendCmd(writer, reader, "EHLO " + host, 250);
            sendCmd(writer, reader, "STARTTLS", 220);

            // 升级到 TLS
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket, host, port, true)) {
                sslSocket.startHandshake();
                smtpConversation(sslSocket, host, from, receiver, useLoginAuth, mailContent);
            }
        }
    }

    /** SMTP 会话流程 */
    private static void smtpConversation(Socket socket, String host, String from, String receiver,
                                          boolean useLoginAuth, String mailContent) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

        readResponse(reader, 220);
        sendCmd(writer, reader, "EHLO " + host, 250);

        // 认证
        String account = CommonConstants.smtpAccount;
        String token = CommonConstants.smtpToken;
        if (!account.isEmpty() && !token.isEmpty()) {
            if (useLoginAuth) {
                sendCmd(writer, reader, "AUTH LOGIN", 334);
                sendCmd(writer, reader, Base64.getEncoder().encodeToString(account.getBytes(StandardCharsets.UTF_8)), 334);
                sendCmd(writer, reader, Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8)), 235);
            } else {
                sendCmd(writer, reader, "AUTH PLAIN " + Base64.getEncoder().encodeToString(
                        ("\0" + account + "\0" + token).getBytes(StandardCharsets.UTF_8)), 235);
            }
        }

        sendCmd(writer, reader, "MAIL FROM:<" + from + ">", 250);
        // 支持多收件人（分号分隔）
        for (String rcpt : receiver.split(";")) {
            sendCmd(writer, reader, "RCPT TO:<" + rcpt.trim() + ">", 250);
        }

        sendCmd(writer, reader, "DATA", 354);
        writer.print(mailContent);
        writer.print(".\r\n");
        writer.flush();
        readResponse(reader, 250);

        sendCmd(writer, reader, "QUIT", 221);
    }

    /** 发件人名称编码：纯 ASCII 原样返回，含非 ASCII 做 RFC 2047 Base64 编码 */
    private static String encodeMailboxName(String name) {
        if (name == null || name.isEmpty()) return "";
        // 纯 ASCII 直接使用，含非 ASCII 则 RFC 2047 Base64 编码
        if (name.chars().allMatch(c -> c < 128)) return name;
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(name.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    // ======================== 品牌化邮件模板 ========================

    /** 品牌主题色（专业蓝，邮件客户端广泛兼容） */
    private static final String BRAND_COLOR = "#1677ff";
    private static final String BRAND_COLOR_LIGHT = "#f0f5ff";

    /**
     * 通用品牌外壳模板（统一头部 + 内容区 + 页脚）
     * <p>
     * 所有业务邮件正文应通过此方法包装，确保品牌一致性。内联 CSS 保证邮件客户端兼容性。
     *
     * @param bodyContent 已构建的内容区 HTML（不含最外层容器）
     */
    public static String wrapBrandTemplate(String bodyContent) {
        String name = CommonConstants.systemName;
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>"
                + "<body style=\"margin:0;padding:0;background:#f5f5f5;"
                + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',sans-serif;\">"
                + "<div style=\"max-width:600px;margin:0 auto;\">"
                // 品牌头部
                + "<div style=\"background:" + BRAND_COLOR + ";color:#fff;padding:24px;text-align:center;\">"
                + "<h1 style=\"margin:0;font-size:22px;font-weight:600;\">" + escapeHtml(name) + "</h1>"
                + "</div>"
                // 内容区
                + "<div style=\"padding:30px 24px;\">"
                + "<div style=\"background:#fff;padding:28px;border-radius:8px;"
                + "box-shadow:0 1px 3px rgba(0,0,0,0.08);\">"
                + bodyContent
                + "</div></div>"
                // 页脚
                + "<div style=\"text-align:center;color:#999;font-size:12px;padding:8px 24px 32px;\">"
                + "<p style=\"margin:4px 0;\">&copy; " + Year.now().getValue() + " " + escapeHtml(name)
                + " &middot; 本邮件由系统自动发送，请勿回复</p>"
                + "</div>"
                + "</div></body></html>";
    }

    /**
     * 构建注册验证码邮件 HTML（品牌化模板）
     */
    public static String buildVerificationCodeEmail(String code, int validMinutes) {
        String body = "<h2 style=\"margin:0 0 16px;color:#333;font-size:18px;\">您的验证码</h2>"
                + "<div style=\"font-size:32px;font-weight:bold;color:" + BRAND_COLOR + ";"
                + "letter-spacing:6px;text-align:center;padding:20px;background:" + BRAND_COLOR_LIGHT + ";"
                + "border-radius:8px;margin:16px 0;\">" + escapeHtml(code) + "</div>"
                + "<p style=\"color:#666;margin:12px 0;line-height:1.6;\">"
                + "验证码有效期 <strong>" + validMinutes + "</strong> 分钟，请勿泄露给他人。</p>";
        return wrapBrandTemplate(body);
    }

    /**
     * 构建密码重置邮件 HTML（品牌化模板）
     */
    public static String buildPasswordResetEmail(String username, String code, int validMinutes) {
        String body = "<p style=\"margin:0 0 16px;color:#333;line-height:1.6;\">您好，<strong>"
                + escapeHtml(username) + "</strong></p>"
                + "<h2 style=\"margin:0 0 16px;color:#333;font-size:18px;\">密码重置验证码</h2>"
                + "<div style=\"font-size:32px;font-weight:bold;color:" + BRAND_COLOR + ";"
                + "letter-spacing:6px;text-align:center;padding:20px;background:" + BRAND_COLOR_LIGHT + ";"
                + "border-radius:8px;margin:16px 0;\">" + escapeHtml(code) + "</div>"
                + "<p style=\"color:#666;margin:12px 0;line-height:1.6;\">"
                + "验证码有效期 <strong>" + validMinutes + "</strong> 分钟。</p>"
                + "<p style=\"color:#999;margin:16px 0 0;font-size:13px;\">"
                + "如果这不是您本人的操作，请忽略此邮件。</p>";
        return wrapBrandTemplate(body);
    }

    /**
     * 构建通知邮件 HTML（用户自定义内容用品牌外壳包装）
     */
    public static String buildNotificationEmail(String contentHtml) {
        return wrapBrandTemplate(contentHtml);
    }

    /** HTML 特殊字符转义（防止注入） */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static void sendCmd(PrintWriter writer, BufferedReader reader, String cmd, int expectedCode) throws IOException {
        writer.print(cmd + "\r\n");
        writer.flush();
        readResponse(reader, expectedCode);
    }

    private static void readResponse(BufferedReader reader, int expectedCode) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.length() < 4 || line.charAt(3) != '-') {
                int code = Integer.parseInt(line.substring(0, 3));
                if (code != expectedCode) {
                    throw new IOException("SMTP error: expected " + expectedCode + ", got " + line);
                }
                return;
            }
        }
    }
}
