package com.apexcare.appointment.service;

import com.apexcare.appointment.dto.AiTriageRequest;
import com.apexcare.appointment.dto.AiTriageResponse;
import com.apexcare.appointment.dto.DrugInteractionCheckRequest;
import com.apexcare.appointment.dto.DrugInteractionCheckResponse;
import com.apexcare.appointment.exception.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rule-based clinical decision support.
 *
 * <p><b>NOT artificial intelligence.</b> This is deterministic keyword
 * matching over free-text symptoms and drug names. It is a first-pass
 * screening aid only. Every output must be reviewed by a licensed clinician.
 *
 * <p><b>Not a medical device.</b> Do not rely on this for diagnosis,
 * treatment, or emergency decisions. In a real emergency, dial local
 * emergency services.
 *
 * <p><b>Clinical validation required</b> before any production use in a
 * regulated environment (FDA/MDR/CE). The rule thresholds below are
 * illustrative examples — not clinically validated.
 */
@Service
public class ClinicalAiService {

    private static final Logger log = LoggerFactory.getLogger(ClinicalAiService.class);

    private static final String DISCLAIMER =
            "This is a rule-based screening aid, not a diagnosis. "
            + "In case of emergency, contact local emergency services immediately. "
            + "All recommendations must be confirmed by a licensed clinician.";

    private static final Pattern WORD_SPLIT = Pattern.compile("[,\\s]+");

    private final Map<String, String> specialtyDoctorCodes;

    public ClinicalAiService(
            @Value("#{${apexcare.ai.specialty-doctor-codes:{}}}")
            Map<String, String> specialtyDoctorCodes) {
        Map<String, String> codes = new LinkedHashMap<>();
        codes.put("General Medicine", "");
        codes.put("Cardiology", "");
        codes.put("Neurology", "");
        codes.put("Orthopedics", "");
        codes.put("Dermatology", "");
        if (specialtyDoctorCodes != null) {
            codes.putAll(specialtyDoctorCodes);
        }
        this.specialtyDoctorCodes = codes;
    }

    // =================================================================
    // TRIAGE
    // =================================================================
    public AiTriageResponse evaluateTriage(AiTriageRequest req) {
        if (req == null) {
            throw new InvalidRequestException("Triage request is required");
        }
        if (req.getSymptoms() == null || req.getSymptoms().isBlank()) {
            throw new InvalidRequestException("Symptoms are required for triage");
        }

        String raw = req.getSymptoms();
        String symptoms = normalize(raw);
        String pain = req.getPainLevel() != null
                ? req.getPainLevel().toUpperCase(Locale.ROOT).trim()
                : "MILD";

        log.info("Triage evaluation requested symptomsLength={} painLevel={}",
                raw.length(), pain);

        AiTriageResponse res = new AiTriageResponse();
        List<String> conditions = new ArrayList<>();
        List<String> redFlags = new ArrayList<>();

        if (matchesAny(symptoms, "chest pain", "chestpain", "heart attack",
                "radiating pain", "loss of consciousness", "unconscious",
                "cardiac arrest", "crushing chest")) {
            res.setUrgency("EMERGENCY");
            res.setRecommendedSpecialty("Cardiology");
            res.setConfidenceScore(0.9);
            res.setSuggestedDoctorCode(codeFor("Cardiology"));
            conditions.add("Acute Coronary Syndrome (suspected)");
            conditions.add("Cardiac ischemia / angina");
            redFlags.add("Immediate Emergency Room evaluation.");
            redFlags.add("Do not drive. Obtain urgent 12-lead ECG.");
            res.setClinicalAdvice("Critical cardiovascular red flags present. "
                    + "Route to ER and notify on-duty cardiologist.");

        } else if (matchesAny(symptoms, "sudden weakness", "slurred speech",
                "facial droop", "seizure", "stroke", "hemiplegia",
                "numbness on one side")) {
            res.setUrgency("EMERGENCY");
            res.setRecommendedSpecialty("Neurology");
            res.setConfidenceScore(0.9);
            res.setSuggestedDoctorCode(codeFor("Neurology"));
            conditions.add("Acute cerebrovascular event (suspected)");
            conditions.add("Transient ischemic attack");
            redFlags.add("Time-critical thrombolysis window (FAST protocol).");
            res.setClinicalAdvice("Immediate stroke-code triage required.");

        } else if (matchesAny(symptoms, "fracture", "joint dislocation",
                "severe bone pain", "cannot bear weight", "deformity")) {
            res.setUrgency("URGENT");
            res.setRecommendedSpecialty("Orthopedics");
            res.setConfidenceScore(0.85);
            res.setSuggestedDoctorCode(codeFor("Orthopedics"));
            conditions.add("Fracture / ligament rupture (suspected)");
            conditions.add("Joint dislocation / effusion");
            redFlags.add("Immobilize the limb. Avoid weight bearing.");
            res.setClinicalAdvice("Arrange immediate X-Ray and orthopedic consult.");

        } else if (matchesAny(symptoms, "skin rash", "itching", "eczema",
                "dermatitis", "lesion", "hives")) {
            res.setUrgency("ROUTINE");
            res.setRecommendedSpecialty("Dermatology");
            res.setConfidenceScore(0.85);
            res.setSuggestedDoctorCode(codeFor("Dermatology"));
            conditions.add("Contact dermatitis");
            conditions.add("Urticaria / eczema flare");
            res.setClinicalAdvice("Outpatient dermatology review recommended.");

        } else if (matchesAny(symptoms, "fever", "cough", "sore throat",
                "cold", "fatigue", "runny nose", "congestion")) {
            res.setUrgency("ROUTINE");
            res.setRecommendedSpecialty("General Medicine");
            res.setConfidenceScore(0.85);
            res.setSuggestedDoctorCode(codeFor("General Medicine"));
            conditions.add("Upper respiratory tract infection");
            conditions.add("Viral syndrome");
            res.setClinicalAdvice("Outpatient consultation. Hydration and "
                    + "antipyretics per clinical judgment.");

        } else {
            res.setUrgency("ROUTINE");
            res.setRecommendedSpecialty("General Medicine");
            res.setConfidenceScore(0.6);
            res.setSuggestedDoctorCode(codeFor("General Medicine"));
            conditions.add("Non-specific symptom presentation");
            res.setClinicalAdvice("General outpatient consultation recommended.");
        }

        if ("CRITICAL".equals(pain) && !"EMERGENCY".equals(res.getUrgency())) {
            res.setUrgency("URGENT");
            redFlags.add("High pain intensity reported — fast-track triage.");
        }

        res.setPossibleConditions(conditions);
        res.setRedFlagAlerts(redFlags);
        res.setDisclaimer(DISCLAIMER);
        return res;
    }

    // =================================================================
    // DRUG INTERACTIONS
    // =================================================================
    public DrugInteractionCheckResponse checkDrugInteractions(DrugInteractionCheckRequest req) {
        if (req == null) {
            throw new InvalidRequestException("Drug interaction request is required");
        }
        if (req.getPrescribedDrugs() == null || req.getPrescribedDrugs().isEmpty()) {
            throw new InvalidRequestException("At least one prescribed drug is required");
        }

        DrugInteractionCheckResponse res = new DrugInteractionCheckResponse();
        List<String> contraindications = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> safeAlternatives = new ArrayList<>();

        Set<String> allergies = tokenize(req.getPatientAllergies());
        Set<String> history = tokenize(req.getPatientMedicalHistory());
        List<String> drugs = req.getPrescribedDrugs();

        log.info("Drug interaction check requested drugsCount={} allergiesCount={} historyCount={}",
                drugs.size(), allergies.size(), history.size());

        boolean penicillinAllergic = allergies.contains("penicillin")
                || allergies.contains("amoxicillin");
        boolean sulfaAllergic = allergies.contains("sulfa")
                || allergies.contains("sulfonamide");
        boolean nsaidCaution = history.contains("ulcer")
                || history.contains("gastric")
                || history.contains("hypertension");

        for (String drug : drugs) {
            if (drug == null || drug.isBlank()) continue;
            String d = drug.toLowerCase(Locale.ROOT).trim();

            // --- Penicillin cross-reactivity ---
            if (penicillinAllergic && matchesAny(d,
                    "amoxicillin", "ampicillin", "augmentin", "penicillin")) {
                contraindications.add(
                        "CRITICAL: Documented penicillin allergy. '"
                        + drug + "' may trigger anaphylaxis.");
                safeAlternatives.add("Azithromycin");
                safeAlternatives.add("Ciprofloxacin");
                safeAlternatives.add("Doxycycline");
            }

            // --- Sulfa cross-reactivity ---
            if (sulfaAllergic && matchesAny(d,
                    "bactrim", "septra", "sulfamethoxazole", "sulfasalazine")) {
                contraindications.add(
                        "CRITICAL: Documented sulfa allergy. '"
                        + drug + "' contains sulfonamides.");
                safeAlternatives.add("Nitrofurantoin");
            }

            // --- NSAID caution ---
            if (nsaidCaution && matchesAny(d,
                    "ibuprofen", "naproxen", "diclofenac", "aspirin")) {
                String reason = history.contains("ulcer")
                        ? "peptic ulcer / GI bleeding risk"
                        : "hypertension";
                warnings.add("MODERATE: NSAID '" + drug
                        + "' may exacerbate " + reason + ".");
                safeAlternatives.add("Paracetamol / acetaminophen");
            }
        }

        if (!contraindications.isEmpty()) {
            res.setHasConflict(true);
            res.setRiskLevel("CRITICAL");
        } else if (!warnings.isEmpty()) {
            res.setHasConflict(true);
            res.setRiskLevel("MODERATE");
        } else {
            res.setHasConflict(false);
            res.setRiskLevel("SAFE");
        }

        res.setContraindications(contraindications);
        res.setWarnings(warnings);
        res.setSafeAlternatives(safeAlternatives);
        res.setDisclaimer(DISCLAIMER);
        return res;
    }

    // =================================================================
    // SOAP NOTES
    // =================================================================
    public String generateSoapNotes(String diagnosis, String complaint, String rawNotes) {
        String c = safe(complaint, "routine symptoms");
        String d = safe(diagnosis, "General clinical assessment");
        String n = safe(rawNotes, "Standard outpatient history");

        return new StringBuilder()
                .append("S: ").append(c).append(". Reported: ").append(n).append(".\n")
                .append("O: Vitals stable. Focused exam findings recorded.\n")
                .append("A: Primary diagnostic impression: ").append(d).append(".\n")
                .append("P: Targeted therapy initiated. Follow-up in 10-14 days "
                        + "if symptoms persist.\n")
                .append("\n")
                .append("[Generated draft — must be reviewed and signed by the "
                        + "treating clinician before entry into the EMR.]")
                .toString();
    }

    // =================================================================
    // HELPERS
    // =================================================================

    private String codeFor(String specialty) {
        String code = specialtyDoctorCodes.get(specialty);
        return code != null ? code : "";
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static boolean matchesAny(String haystack, String... needles) {
        if (haystack == null || haystack.isEmpty()) return false;
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }

    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        String[] tokens = WORD_SPLIT.split(text.toLowerCase(Locale.ROOT).trim());
        Set<String> out = new java.util.HashSet<>();
        for (String t : tokens) {
            if (!t.isBlank()) out.add(t);
        }
        return out;
    }

    private static String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
