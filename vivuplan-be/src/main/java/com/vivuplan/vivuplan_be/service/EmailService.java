package com.vivuplan.vivuplan_be.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.email.from:${SMTP_FROM:no-reply@vivuplan.xyz}}")
    private String fromEmail;

    @Value("${app.email.from-name:${SMTP_FROM_NAME:VivuPlan}}")
    private String fromName;

    @Value("${app.frontend-url:${APP_FRONTEND_URL:https://vivuplan.xyz}}")
    private String frontendUrl;

    @Async
    public void sendRegistrationOtpAsync(String toEmail, String name, String otp, long expiresInMinutes) {
        try {
            sendRegistrationOtp(toEmail, name, otp, expiresInMinutes);
        } catch (Exception e) {
            log.error("Failed to send registration OTP email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    @Async
    public void sendPasswordResetOtpAsync(String toEmail, String name, String otp, long expiresInMinutes) {
        try {
            sendPasswordResetOtp(toEmail, name, otp, expiresInMinutes);
        } catch (Exception e) {
            log.error("Failed to send password reset OTP email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    private void sendRegistrationOtp(String toEmail, String name, String otp, long expiresInMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Mã xác nhận đăng ký VivuPlan");
            helper.setText(buildRegistrationOtpHtml(name, otp, expiresInMinutes), true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("Không thể gửi email xác nhận", e);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể gửi email xác nhận", e);
        }
    }

    private void sendPasswordResetOtp(String toEmail, String name, String otp, long expiresInMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Mã đặt lại mật khẩu VivuPlan");
            helper.setText(buildPasswordResetOtpHtml(name, otp, expiresInMinutes), true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("Không thể gửi email đặt lại mật khẩu", e);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể gửi email đặt lại mật khẩu", e);
        }
    }

    private String buildRegistrationOtpHtml(String name, String otp, long expiresInMinutes) {
        String safeName = HtmlUtils.htmlEscape(name == null || name.isBlank() ? "bạn" : name.trim());
        String safeOtp = HtmlUtils.htmlEscape(otp);
        String safeFrontendUrl = HtmlUtils.htmlEscape(frontendUrl);

        return """
                <!doctype html>
                <html lang="vi">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Mã xác nhận VivuPlan</title>
                </head>
                <body style="margin:0;background:#F5FAF9;font-family:Inter,Segoe UI,Arial,sans-serif;color:#0B1F3A;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#F5FAF9;padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#FFFFFF;border:1px solid #DCEBE9;border-radius:18px;overflow:hidden;box-shadow:0 18px 45px rgba(6,42,92,0.08);">
                          <tr>
                            <td style="padding:28px 32px 20px;border-bottom:1px solid #E7F2F0;">
                              <div style="font-size:22px;font-weight:800;letter-spacing:-0.2px;line-height:1;">
                                <span style="color:#20BDB4;">Vivu</span><span style="color:#062A5C;">Plan</span>
                              </div>
                              <div style="font-size:13px;color:#65758B;margin-top:8px;">Lập kế hoạch du lịch thông minh</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:30px 32px 10px;">
                              <div style="display:inline-block;padding:7px 12px;border-radius:999px;background:#E9FAF7;color:#168E88;font-size:13px;font-weight:700;margin-bottom:18px;">Xác nhận email</div>
                              <h1 style="margin:0 0 12px;font-size:28px;line-height:1.2;color:#071B3A;font-weight:800;">Hoàn tất đăng ký tài khoản</h1>
                              <p style="margin:0;color:#536278;font-size:15px;line-height:1.7;">Chào %s, nhập mã xác nhận bên dưới để kích hoạt tài khoản VivuPlan của bạn.</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:18px 32px 8px;">
                              <div style="background:#F7FBFA;border:1px solid #DCEBE9;border-radius:16px;padding:24px;text-align:center;">
                                <div style="font-size:12px;letter-spacing:0.16em;text-transform:uppercase;color:#78908D;font-weight:800;margin-bottom:10px;">Mã xác nhận</div>
                                <div style="font-size:36px;line-height:1;font-weight:800;letter-spacing:0.18em;color:#062A5C;">%s</div>
                                <div style="font-size:13px;color:#78908D;margin-top:14px;">Mã có hiệu lực trong %d phút.</div>
                              </div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:18px 32px 30px;">
                              <p style="margin:0;color:#536278;font-size:14px;line-height:1.7;">Nếu bạn không yêu cầu tạo tài khoản, có thể bỏ qua email này. Để bảo mật tài khoản, vui lòng không chia sẻ mã này với bất kỳ ai.</p>
                              <div style="margin-top:22px;padding-top:18px;border-top:1px solid #E7F2F0;color:#8A98AA;font-size:12px;line-height:1.6;">
                                Email này được gửi từ VivuPlan. Truy cập <a href="%s" style="color:#168E88;text-decoration:none;font-weight:700;">VivuPlan</a> để tiếp tục lập kế hoạch chuyến đi của bạn.
                              </div>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(safeName, safeOtp, expiresInMinutes, safeFrontendUrl);
    }

    private String buildPasswordResetOtpHtml(String name, String otp, long expiresInMinutes) {
        String safeName = HtmlUtils.htmlEscape(name == null || name.isBlank() ? "bạn" : name.trim());
        String safeOtp = HtmlUtils.htmlEscape(otp);
        String safeFrontendUrl = HtmlUtils.htmlEscape(frontendUrl);

        return """
                <!doctype html>
                <html lang="vi">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Mã đặt lại mật khẩu VivuPlan</title>
                </head>
                <body style="margin:0;background:#F5FAF9;font-family:Inter,Segoe UI,Arial,sans-serif;color:#0B1F3A;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#F5FAF9;padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#FFFFFF;border:1px solid #DCEBE9;border-radius:18px;overflow:hidden;box-shadow:0 18px 45px rgba(6,42,92,0.08);">
                          <tr>
                            <td style="padding:30px 32px 10px;">
                              <div style="display:inline-block;padding:7px 12px;border-radius:999px;background:#E9FAF7;color:#168E88;font-size:13px;font-weight:700;margin-bottom:18px;">Bảo mật tài khoản</div>
                              <h1 style="margin:0 0 12px;font-size:28px;line-height:1.2;color:#071B3A;font-weight:800;">Đặt lại mật khẩu VivuPlan</h1>
                              <p style="margin:0;color:#536278;font-size:15px;line-height:1.7;">Chào %s, dùng mã xác nhận bên dưới để tạo mật khẩu mới cho tài khoản của bạn.</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:18px 32px 8px;">
                              <div style="background:#F7FBFA;border:1px solid #DCEBE9;border-radius:16px;padding:24px;text-align:center;">
                                <div style="font-size:12px;letter-spacing:0.16em;text-transform:uppercase;color:#78908D;font-weight:800;margin-bottom:10px;">Mã xác nhận</div>
                                <div style="font-size:36px;line-height:1;font-weight:800;letter-spacing:0.18em;color:#062A5C;">%s</div>
                                <div style="font-size:13px;color:#78908D;margin-top:14px;">Mã có hiệu lực trong %d phút.</div>
                              </div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:18px 32px 30px;">
                              <p style="margin:0;color:#536278;font-size:14px;line-height:1.7;">Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này. Vui lòng không chia sẻ mã này với bất kỳ ai.</p>
                              <div style="margin-top:22px;padding-top:18px;border-top:1px solid #E7F2F0;color:#8A98AA;font-size:12px;line-height:1.6;">
                                Email này được gửi từ VivuPlan. Truy cập <a href="%s" style="color:#168E88;text-decoration:none;font-weight:700;">VivuPlan</a> để tiếp tục.
                              </div>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(safeName, safeOtp, expiresInMinutes, safeFrontendUrl);
    }
}
