package com.apexcare.auth.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CaptchaResponse {

    private String captchaId;
    private String challengeSvg;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant expiresAt;

    public CaptchaResponse() {}

    public CaptchaResponse(String captchaId, String challengeSvg, Instant expiresAt) {
        this.captchaId = captchaId;
        this.challengeSvg = challengeSvg;
        this.expiresAt = expiresAt;
    }

    public String getCaptchaId() { return captchaId; }
    public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }

    public String getChallengeSvg() { return challengeSvg; }
    public void setChallengeSvg(String challengeSvg) { this.challengeSvg = challengeSvg; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
