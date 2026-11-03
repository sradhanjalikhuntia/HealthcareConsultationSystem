package com.apexcare.appointment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Result of a drug–drug / drug–allergy interaction check.
 *
 * <p><b>Not a substitute for professional review.</b> The rule engine behind
 * this response covers only a limited set of interactions. Absence of a
 * conflict here does not mean the prescription is safe — a licensed
 * pharmacist or clinician must review every prescription.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DrugInteractionCheckResponse {

    private boolean hasConflict;

    private DrugRiskLevel riskLevel;

    private List<String> contraindications;
    private List<String> warnings;
    private List<String> safeAlternatives;

    /**
     * Human-readable disclaimer that must be surfaced to the UI client.
     * Example: "Rule-based screening only — not a diagnosis. Confirm with a clinician."
     */
    private String disclaimer;

    // ---- getters / setters ----

    public boolean isHasConflict() { return hasConflict; }
    public void setHasConflict(boolean hasConflict) { this.hasConflict = hasConflict; }

    public DrugRiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(DrugRiskLevel riskLevel) { this.riskLevel = riskLevel; }
    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel == null ? null : DrugRiskLevel.valueOf(riskLevel.toUpperCase());
    }

    public List<String> getContraindications() { return contraindications; }
    public void setContraindications(List<String> contraindications) { this.contraindications = contraindications; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }

    public List<String> getSafeAlternatives() { return safeAlternatives; }
    public void setSafeAlternatives(List<String> safeAlternatives) { this.safeAlternatives = safeAlternatives; }

    public String getDisclaimer() { return disclaimer; }
    public void setDisclaimer(String disclaimer) { this.disclaimer = disclaimer; }
}
