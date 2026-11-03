package com.apexcare.auth.service;

import com.apexcare.auth.dto.SendOtpResponse;
import com.apexcare.auth.exception.InvalidRequestException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for issuing, delivering, and validating 6-digit Email Verification OTPs.
 * Only sends OTP directly to the user's actual email address via SMTP.
 * Never leaks the OTP to API responses or frontend clients.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private static final int OTP_TTL_SECONDS = 600; // 10 minutes
    private static final int RESEND_COOLDOWN_SECONDS = 30; // 30s rate limit
    private static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String mailHost;

    private final Map<String, OtpRecord> otpStore = new ConcurrentHashMap<>();

    private record OtpRecord(
            String otp,
            Instant expiresAt,
            AtomicInteger attempts,
            Instant createdAt
    ) {}

    public EmailVerificationService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    /**
     * Generates a 6-digit OTP and delivers it directly to the user's email address.
     * Throws an exception if the email delivery fails or if SMTP is not configured.
     */
    public SendOtpResponse sendVerificationOtp(String email) {
        if (!StringUtils.hasText(email)) {
            throw new InvalidRequestException("Email address is required");
        }

        String normalizedEmail = email.trim().toLowerCase();

        // Rate limit: check if an OTP was issued recently
        OtpRecord existing = otpStore.get(normalizedEmail);
        if (existing != null) {
            long secondsSinceLast = Duration.between(existing.createdAt(), Instant.now()).toSeconds();
            if (secondsSinceLast < RESEND_COOLDOWN_SECONDS) {
                long waitSeconds = RESEND_COOLDOWN_SECONDS - secondsSinceLast;
                throw new InvalidRequestException("Please wait " + waitSeconds + " seconds before requesting a new verification code.");
            }
        }

        // Generate 6-digit secure numeric OTP
        int code = 100_000 + RANDOM.nextInt(900_000);
        String otp = String.valueOf(code);
        Instant expiresAt = Instant.now().plusSeconds(OTP_TTL_SECONDS);

        // Deliver OTP to user's real email inbox
        boolean emailSent = sendRealEmail(normalizedEmail, otp);
        if (!emailSent) {
            throw new InvalidRequestException(
                    "Unable to deliver verification code to " + normalizedEmail +
                    ". Please make sure the email address is correct, or configure valid SMTP credentials in backend settings."
            );
        }

        otpStore.put(normalizedEmail, new OtpRecord(otp, expiresAt, new AtomicInteger(0), Instant.now()));
        log.info("📧 Verification OTP [{}] successfully delivered to inbox of {}", otp, normalizedEmail);

        return new SendOtpResponse(
                normalizedEmail,
                "A 6-digit verification code has been sent to " + normalizedEmail + ". Please check your email inbox.",
                OTP_TTL_SECONDS
        );
    }

    /**
     * Validates an OTP for the given email.
     * Consumes the OTP if valid.
     */
    public boolean verifyOtp(String email, String otp) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(otp)) {
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase();
        String normalizedOtp = otp.trim();

        OtpRecord record = otpStore.get(normalizedEmail);
        if (record == null) {
            log.warn("OTP verification failed: no OTP request found for email={}", normalizedEmail);
            return false;
        }

        if (Instant.now().isAfter(record.expiresAt())) {
            otpStore.remove(normalizedEmail);
            log.warn("OTP verification failed: code expired for email={}", normalizedEmail);
            return false;
        }

        int currentAttempts = record.attempts().incrementAndGet();
        if (currentAttempts > MAX_ATTEMPTS) {
            otpStore.remove(normalizedEmail);
            log.warn("OTP verification failed: max attempts exceeded for email={}", normalizedEmail);
            throw new InvalidRequestException("Too many incorrect verification attempts. Please request a new code.");
        }

        if (record.otp().equals(normalizedOtp)) {
            otpStore.remove(normalizedEmail);
            log.info("OTP verified successfully for email={}", normalizedEmail);
            return true;
        }

        log.warn("OTP verification failed: incorrect code for email={}, attempt={}/{}",
                normalizedEmail, currentAttempts, MAX_ATTEMPTS);
        return false;
    }

    /**
     * Sends the OTP email via JavaMailSender.
     */
    private boolean sendRealEmail(String toEmail, String otp) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || !StringUtils.hasText(mailHost) || !StringUtils.hasText(mailUsername) || !StringUtils.hasText(mailPassword)) {
            log.error("SMTP is not configured (mailHost='{}', mailUsername='{}', password configured={}). Cannot send real email.",
                    mailHost, mailUsername, StringUtils.hasText(mailPassword));
            return false;
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String senderName = "ApexCare Health Network";
            helper.setFrom(mailUsername, senderName);
            helper.setTo(toEmail);
            helper.setSubject("ApexCare - Email Verification Code: " + otp);

            String html = """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0b1120; color: #f8fafc; padding: 24px; }
                    .card { max-width: 520px; margin: 0 auto; background-color: #1e293b; border: 1px solid #334155; border-radius: 12px; padding: 32px; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
                    .logo { text-align: center; margin-bottom: 24px; }
                    .logo-title { font-size: 24px; font-weight: 700; color: #38bdf8; margin: 0; }
                    .logo-sub { font-size: 13px; color: #94a3b8; margin-top: 4px; }
                    .heading { font-size: 18px; font-weight: 600; color: #f1f5f9; text-align: center; margin-bottom: 8px; }
                    .desc { font-size: 14px; color: #cbd5e1; text-align: center; line-height: 1.6; margin-bottom: 24px; }
                    .otp-box { background: linear-gradient(135deg, rgba(56, 189, 248, 0.15), rgba(129, 140, 248, 0.15)); border: 2px dashed #38bdf8; border-radius: 10px; padding: 18px; text-align: center; margin-bottom: 24px; }
                    .otp-code { font-family: 'Courier New', monospace; font-size: 36px; font-weight: 800; letter-spacing: 8px; color: #38bdf8; margin: 0; }
                    .expiry { font-size: 13px; color: #f59e0b; margin-top: 8px; font-weight: 500; }
                    .footer { text-align: center; font-size: 12px; color: #64748b; margin-top: 24px; line-height: 1.5; border-top: 1px solid #334155; padding-top: 16px; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="logo">
                      <div class="logo-title">🏥 APEXCARE</div>
                      <div class="logo-sub">Smart Healthcare Management Network</div>
                    </div>
                    <div class="heading">Verify Your Email Address</div>
                    <p class="desc">
                      Thank you for choosing ApexCare. Please enter the verification code below to verify your email address and activate your account.
                    </p>
                    <div class="otp-box">
                      <div class="otp-code">""" + otp + """
                      </div>
                      <div class="expiry">⏱️ Valid for 10 minutes</div>
                    </div>
                    <p class="desc" style="font-size: 13px; color: #94a3b8;">
                      If you did not initiate this registration request, please disregard this email. Your account will not be created.
                    </p>
                    <div class="footer">
                      ApexCare Hospital Information Systems &bull; Enterprise Security &bull; Confidential
                    </div>
                  </div>
                </body>
                </html>
                """;

            helper.setText(html, true);
            sender.send(message);
            return true;
        } catch (Exception ex) {
            log.error("Failed to send verification email to {}: {}", toEmail, ex.getMessage());
            return false;
        }
    }

    /**
     * Purges expired OTP records periodically every minute to avoid memory leaks.
     */
    @Scheduled(fixedRate = 60_000)
    public void cleanupExpiredOtps() {
        Instant now = Instant.now();
        otpStore.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }
}
