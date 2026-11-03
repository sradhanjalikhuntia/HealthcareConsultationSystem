package com.apexcare.billing.dto;

import com.apexcare.billing.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payment submission payload.
 *
 * <p><b>PCI-DSS note:</b> This DTO deliberately does NOT accept raw card
 * numbers, CVVs, or expiry dates. Those must be captured and tokenized
 * by the client-side payment gateway SDK (Stripe.js, Razorpay Checkout,
 * etc.). The backend receives only the gateway's opaque token or the
 * post-charge transaction reference.
 *
 * <p>If a caller sends {@code "cardNumber"} or {@code "cvv"} in the JSON
 * body, Jackson will reject the request with a 400 (unknown property) —
 * by design.
 */
public class PaymentRequest {

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.01", message = "Payment amount must be greater than zero")
    private BigDecimal amount;

    /**
     * Gateway token from the client SDK (e.g. Razorpay token, Stripe
     * PaymentMethod ID). This is what the backend charges against.
     * Never a raw card number.
     */
    @Size(max = 128, message = "paymentToken must be at most 128 characters")
    private String paymentToken;

    /**
     * Transaction reference returned by the gateway after successful charge,
     * or a manually generated reference for cash / cheque payments.
     */
    @Size(max = 80, message = "transactionReference must be at most 80 characters")
    private String transactionReference;

    @Size(max = 500, message = "notes must be at most 500 characters")
    private String notes;

    // ---- getters / setters ----

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getPaymentToken() { return paymentToken; }
    public void setPaymentToken(String paymentToken) { this.paymentToken = paymentToken; }

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
