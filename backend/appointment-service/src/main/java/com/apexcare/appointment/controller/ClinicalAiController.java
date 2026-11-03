package com.apexcare.appointment.controller;

import com.apexcare.appointment.dto.AiTriageRequest;
import com.apexcare.appointment.dto.AiTriageResponse;
import com.apexcare.appointment.dto.ApiResponse;
import com.apexcare.appointment.dto.DrugInteractionCheckRequest;
import com.apexcare.appointment.dto.DrugInteractionCheckResponse;
import com.apexcare.appointment.service.ClinicalAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/appointments/ai")
@Tag(name = "Clinical AI Intelligence")
public class ClinicalAiController {

    private final ClinicalAiService aiService;

    public ClinicalAiController(ClinicalAiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/triage")
    @Operation(summary = "AI-assisted clinical triage and specialty routing")
    public ResponseEntity<ApiResponse<AiTriageResponse>> evaluateTriage(@Valid @RequestBody AiTriageRequest request) {
        AiTriageResponse response = aiService.evaluateTriage(request);
        return ResponseEntity.ok(ApiResponse.ok("AI Triage evaluation completed", response));
    }

    @PostMapping("/check-drugs")
    @Operation(summary = "Drug-Allergy contraindication and clinical conflict validator")
    public ResponseEntity<ApiResponse<DrugInteractionCheckResponse>> checkDrugInteractions(@RequestBody DrugInteractionCheckRequest request) {
        DrugInteractionCheckResponse response = aiService.checkDrugInteractions(request);
        return ResponseEntity.ok(ApiResponse.ok("Drug safety check completed", response));
    }

    @PostMapping("/generate-soap")
    @Operation(summary = "Auto-generate standardized SOAP medical notes")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateSoap(
            @RequestParam(required = false) String diagnosis,
            @RequestParam(required = false) String complaint,
            @RequestParam(required = false) String notes
    ) {
        String soapNotes = aiService.generateSoapNotes(diagnosis, complaint, notes);
        return ResponseEntity.ok(ApiResponse.ok("SOAP notes generated", Map.of("soapNotes", soapNotes)));
    }
}
