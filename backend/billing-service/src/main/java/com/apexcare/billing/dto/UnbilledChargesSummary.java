package com.apexcare.billing.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UnbilledChargesSummary {

    private UUID patientId;
    private BigDecimal consultationFee = BigDecimal.ZERO;
    private BigDecimal medicineCharges = BigDecimal.ZERO;
    private BigDecimal labCharges = BigDecimal.ZERO;
    private BigDecimal wardCharges = BigDecimal.ZERO;
    private BigDecimal otherCharges = BigDecimal.ZERO;
    private BigDecimal totalCharges = BigDecimal.ZERO;
    private List<ChargeLineItem> items = new ArrayList<>();

    public static class ChargeLineItem {
        private String type; // "CONSULTATION", "MEDICINE", "LAB_TEST", "WARD", "OTHER"
        private String description;
        private BigDecimal amount;
        private String date;

        public ChargeLineItem() {}

        public ChargeLineItem(String type, String description, BigDecimal amount, String date) {
            this.type = type;
            this.description = description;
            this.amount = amount;
            this.date = date;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
    }

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }

    public BigDecimal getConsultationFee() { return consultationFee; }
    public void setConsultationFee(BigDecimal consultationFee) { this.consultationFee = consultationFee; }

    public BigDecimal getMedicineCharges() { return medicineCharges; }
    public void setMedicineCharges(BigDecimal medicineCharges) { this.medicineCharges = medicineCharges; }

    public BigDecimal getLabCharges() { return labCharges; }
    public void setLabCharges(BigDecimal labCharges) { this.labCharges = labCharges; }

    public BigDecimal getWardCharges() { return wardCharges; }
    public void setWardCharges(BigDecimal wardCharges) { this.wardCharges = wardCharges; }

    public BigDecimal getOtherCharges() { return otherCharges; }
    public void setOtherCharges(BigDecimal otherCharges) { this.otherCharges = otherCharges; }

    public BigDecimal getTotalCharges() { return totalCharges; }
    public void setTotalCharges(BigDecimal totalCharges) { this.totalCharges = totalCharges; }

    public List<ChargeLineItem> getItems() { return items; }
    public void setItems(List<ChargeLineItem> items) { this.items = items; }
}
