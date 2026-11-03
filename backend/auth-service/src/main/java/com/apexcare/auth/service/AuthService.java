package com.apexcare.auth.service;

import com.apexcare.auth.dto.AuthTokenResponse;
import com.apexcare.auth.dto.ForgotPasswordRequest;
import com.apexcare.auth.dto.LoginRequest;
import com.apexcare.auth.dto.RegisterRequest;
import com.apexcare.auth.dto.SendOtpRequest;
import com.apexcare.auth.dto.SendOtpResponse;
import com.apexcare.auth.dto.UsernameCheckResponse;
import com.apexcare.auth.dto.VerifyOtpRequest;
import com.apexcare.auth.entity.Role;
import com.apexcare.auth.entity.User;
import com.apexcare.auth.exception.InvalidCredentialsException;
import com.apexcare.auth.exception.InvalidRequestException;
import com.apexcare.auth.repository.UserRepository;
import com.apexcare.auth.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * Constant hash used to equalize timing when the username does not exist.
     * Prevents username enumeration via response-time side channel.
     * The value itself is irrelevant — it just needs to be a valid BCrypt hash.
     */
    private static final String DUMMY_HASH =
            "$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final Set<Role> SELF_REGISTER_ROLES = EnumSet.of(
            Role.PATIENT,
            Role.DOCTOR,
            Role.NURSE,
            Role.RECEPTIONIST,
            Role.BILLING_ADMIN,
            Role.ADMIN);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CaptchaService captchaService;
    private final EmailVerificationService emailVerificationService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       CaptchaService captchaService,
                       EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.captchaService = captchaService;
        this.emailVerificationService = emailVerificationService;
    }

    // -----------------------------------------------------------------
    // REGISTER — role-based public self-registration with Email OTP
    // -----------------------------------------------------------------
    private static final java.util.regex.Pattern STRICT_EMAIL_PATTERN = java.util.regex.Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,15}$"
    );

    private static final java.util.regex.Pattern STRICT_USERNAME_PATTERN = java.util.regex.Pattern.compile(
            "^[a-zA-Z0-9._-]+$"
    );

    private static final java.util.Set<String> DISPOSABLE_EMAIL_DOMAINS = java.util.Set.of(
            "mailinator.com", "tempmail.com", "10minutemail.com", "guerrillamail.com",
            "throwawaymail.com", "yopmail.com", "sharklasers.com", "dispostable.com",
            "getairmail.com", "trashmail.com", "temp-mail.org", "fake.com"
    );

    /**
     * Checks whether a username conforms to the format policy and is not already registered.
     */
    public UsernameCheckResponse checkUsernameAvailability(String rawUsername) {
        if (!StringUtils.hasText(rawUsername)) {
            return new UsernameCheckResponse(false, "", "Username cannot be empty", List.of());
        }

        String username = rawUsername.trim().toLowerCase();

        if (username.length() < 3 || username.length() > 80) {
            return new UsernameCheckResponse(false, username,
                    "Username must be between 3 and 80 characters", List.of());
        }

        if (!STRICT_USERNAME_PATTERN.matcher(username).matches()) {
            return new UsernameCheckResponse(false, username,
                    "Username may only contain letters, numbers, '.', '_', and '-' (no spaces)", List.of());
        }

        boolean exists = userRepository.existsByUsernameIgnoreCase(username);
        if (exists) {
            List<String> suggestions = generateUsernameSuggestions(username);
            return new UsernameCheckResponse(false, username,
                    "Username '" + username + "' is already taken. Please choose another username.", suggestions);
        }

        return new UsernameCheckResponse(true, username, "Username is available", List.of());
    }

    private List<String> generateUsernameSuggestions(String base) {
        List<String> suggestions = new ArrayList<>();
        int year = java.time.Year.now().getValue();
        String[] suffixes = {"", "_care", "_med", "99", String.valueOf(year)};
        Random random = new Random();

        for (String suffix : suffixes) {
            String candidate = suffix.isEmpty() ? (base + (100 + random.nextInt(900))) : (base + suffix);
            if (!userRepository.existsByUsernameIgnoreCase(candidate) && !suggestions.contains(candidate)) {
                suggestions.add(candidate);
                if (suggestions.size() >= 3) break;
            }
        }
        return suggestions;
    }

    public void validateEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new InvalidRequestException("Email address is required");
        }
        String cleanEmail = email.trim().toLowerCase();
        if (!STRICT_EMAIL_PATTERN.matcher(cleanEmail).matches()) {
            throw new InvalidRequestException("Please provide a valid email address (e.g. yourname@domain.com)");
        }
        int atIndex = cleanEmail.indexOf('@');
        if (atIndex > 0) {
            String domain = cleanEmail.substring(atIndex + 1);
            if (DISPOSABLE_EMAIL_DOMAINS.contains(domain)) {
                throw new InvalidRequestException("Temporary or disposable email domains are not allowed. Please use a valid email.");
            }
            String[] parts = domain.split("\\.");
            if (parts.length < 2 || parts[parts.length - 1].length() < 2) {
                throw new InvalidRequestException("Invalid email domain. Please enter a valid domain like .com, .org, or .in");
            }
        }
    }

    /**
     * Issues an email verification OTP code after checking username uniqueness (if provided),
     * email format, disposable status, and account non-existence.
     */
    public SendOtpResponse sendVerificationOtp(SendOtpRequest request) {
        if (request == null || !StringUtils.hasText(request.getEmail())) {
            throw new InvalidRequestException("Email address is required");
        }

        // Validate username uniqueness upfront if provided so user isn't stuck after verifying OTP
        if (StringUtils.hasText(request.getUsername())) {
            UsernameCheckResponse usernameCheck = checkUsernameAvailability(request.getUsername());
            if (!usernameCheck.isAvailable()) {
                throw new InvalidRequestException(usernameCheck.getMessage());
            }
        }

        validateEmail(request.getEmail());
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new InvalidRequestException("An account with this email address is already registered. Please sign in instead.");
        }

        return emailVerificationService.sendVerificationOtp(email);
    }

    /**
     * Validates an OTP code without registering the user.
     */
    public boolean verifyOtp(VerifyOtpRequest request) {
        if (request == null || !StringUtils.hasText(request.getEmail()) || !StringUtils.hasText(request.getOtp())) {
            throw new InvalidRequestException("Email address and verification code are required");
        }
        validateEmail(request.getEmail());
        return emailVerificationService.verifyOtp(request.getEmail(), request.getOtp());
    }

    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {

        // Optional CAPTCHA check if provided (mandatory verification is enforced via Email OTP)
        if (StringUtils.hasText(request.getCaptchaId()) && StringUtils.hasText(request.getCaptchaAnswer())) {
            captchaService.validateCaptcha(request.getCaptchaId(), request.getCaptchaAnswer());
        }

        validateEmail(request.getEmail());

        String username = request.getUsername().trim().toLowerCase();
        String email    = request.getEmail().trim().toLowerCase();

        // 1. Mandatory Email OTP Verification
        if (!StringUtils.hasText(request.getOtp())) {
            throw new InvalidRequestException(
                    "Email verification code is required. Please verify your email before registering.");
        }
        boolean otpValid = emailVerificationService.verifyOtp(email, request.getOtp());
        if (!otpValid) {
            throw new InvalidRequestException(
                    "Invalid or expired email verification code. Please check your email or request a new code.");
        }

        // 2. Case-insensitive uniqueness check BEFORE saving
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new InvalidRequestException("Username '" + username + "' is already taken. Please choose another username.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new InvalidRequestException("An account with email '" + email + "' is already registered. Please sign in instead.");
        }

        Role role = request.getRole() == null ? Role.PATIENT : request.getRole();
        if (!SELF_REGISTER_ROLES.contains(role)) {
            throw new InvalidRequestException(
                    "This role cannot be used for self-registration");
        }

        // Security restriction: ADMIN role registration strictly requires secret code "Abhi"
        if (role == Role.ADMIN) {
            String adminCode = request.getAdminCode();
            if (!StringUtils.hasText(adminCode) || !"Abhi".equals(adminCode.trim())) {
                throw new InvalidRequestException(
                        "Registration as Administrator is restricted. You must provide the valid Admin Security Code to create an Admin account.");
            }
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setRole(role);
        user.setEmailVerified(true); // Marked verified!

        if (role == Role.PATIENT) {
            user.setLinkedPatientId(UUID.randomUUID());
        }
        if (role == Role.DOCTOR) {
            user.setDoctorCode(allocateDoctorCode(request.getDoctorCode()));
        }

        // Staff roles require administrator approval before activation:
        // DOCTOR, NURSE, BILLING_ADMIN, RECEPTIONIST
        boolean isStaff = (role == Role.DOCTOR || role == Role.NURSE 
                        || role == Role.BILLING_ADMIN || role == Role.RECEPTIONIST);

        if (isStaff) {
            user.setEnabled(false); // Awaiting Administrator Approval
        } else {
            user.setEnabled(true);
            user.recordLogin();
        }

        User saved = userRepository.save(user);

        log.info("User registered username={} userId={} role={} enabled={} emailVerified=true",
                saved.getUsername(), saved.getId(), saved.getRole(), saved.isEnabled());

        if (isStaff) {
            AuthTokenResponse resp = new AuthTokenResponse();
            resp.setUserId(saved.getId());
            resp.setUsername(saved.getUsername());
            resp.setRole(saved.getRole());
            resp.setFirstName(saved.getFirstName());
            resp.setLastName(saved.getLastName());
            resp.setCreatedAt(saved.getCreatedAt());
            return resp; // token left null so unapproved staff cannot auto-authenticate
        }

        return toToken(saved);
    }

    private String allocateDoctorCode(String requested) {
        if (StringUtils.hasText(requested)) {
            String code = requested.trim().toUpperCase();
            if (userRepository.existsByDoctorCode(code)) {
                throw new InvalidRequestException("Doctor code is already in use");
            }
            return code;
        }
        String generated;
        do {
            generated = "D" + UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 7)
                    .toUpperCase();
        } while (userRepository.existsByDoctorCode(generated));
        return generated;
    }

    // -----------------------------------------------------------------
    // LOGIN
    // -----------------------------------------------------------------
    @Transactional
    public AuthTokenResponse login(LoginRequest request) {

        // Optional CAPTCHA check if provided (mandatory verification is enforced via Email OTP)
        if (StringUtils.hasText(request.getCaptchaId()) && StringUtils.hasText(request.getCaptchaAnswer())) {
            captchaService.validateCaptcha(request.getCaptchaId(), request.getCaptchaAnswer());
        }

        String username = request.getUsername() == null
                ? ""
                : request.getUsername().trim().toLowerCase();

        User user = userRepository.findByUsernameIgnoreCase(username)
                .or(() -> userRepository.findByEmailIgnoreCase(username))
                .orElse(null);

        if (user == null) {
            // Perform a dummy hash to equalize response timing (anti-enumeration).
            passwordEncoder.matches(request.getPassword(), DUMMY_HASH);
            log.warn("Login failed (unknown username) username={}", username);
            throw InvalidCredentialsException.badCredentials();
        }

        boolean passwordMatches =
                passwordEncoder.matches(request.getPassword(), user.getPasswordHash());

        if (!passwordMatches) {
            log.warn("Login failed (bad password) username={}", username);
            throw InvalidCredentialsException.badCredentials();
        }

        if (!user.isEnabled()) {
            log.warn("Login blocked (account pending admin approval or disabled) username={}", username);
            throw new InvalidRequestException(
                    "Your staff account is pending Administrator approval. You cannot sign in until an Admin verifies and accepts your account.");
        }

        // Email Verification Enforcement
        if (!user.isEmailVerified()) {
            log.warn("Login blocked (email not verified) username={}", username);
            throw new InvalidRequestException("Your email address is not verified. Please verify your email before signing in.");
        }

        user.recordLogin();
        User saved = userRepository.save(user);

        log.info("Login success username={} role={} lastLoginAt={}",
                saved.getUsername(), saved.getRole(), saved.getLastLoginAt());

        return toToken(saved);
    }

    // -----------------------------------------------------------------
    // FORGOT PASSWORD — no user enumeration
    // -----------------------------------------------------------------
    @Transactional(readOnly = true)
    public void forgotPassword(ForgotPasswordRequest request) {
        if (request.getEmail() == null) {
            return;   // silently succeed
        }
        String email = request.getEmail().trim().toLowerCase();

        userRepository.findByEmailIgnoreCase(email)
                .ifPresent(user -> log.info(
                        "Password reset requested userId={} (delivery is simulated)",
                        user.getId()));

        // Deliberately do not reveal whether the email exists.
    }

    // -----------------------------------------------------------------
    // TOKEN BUILDER
    // -----------------------------------------------------------------
    private AuthTokenResponse toToken(User user) {
        AuthTokenResponse response = new AuthTokenResponse();

        response.setToken(jwtService.generateToken(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getLinkedPatientId(),
                user.getDoctorCode()));

        response.setExpiresAt(jwtService.expirationInstant());
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRole(user.getRole());
        response.setLinkedPatientId(user.getLinkedPatientId());
        response.setDoctorCode(user.getDoctorCode());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setCreatedAt(user.getCreatedAt());
        response.setLastLoginAt(user.getLastLoginAt());
        return response;
    }
}
