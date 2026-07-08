package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.CommonConstants;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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

            // 构造邮件内容
            String encodedSubject = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "=?";
            StringBuilder mail = new StringBuilder();
            mail.append("To: ").append(receiver).append("\r\n");
            mail.append("From: ").append(CommonConstants.systemName).append(" <").append(from).append(">\r\n");
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
