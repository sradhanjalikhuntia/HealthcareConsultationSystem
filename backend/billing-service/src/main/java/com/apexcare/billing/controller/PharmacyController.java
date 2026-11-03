package com.apexcare.billing.controller;

import com.apexcare.billing.dto.AddPharmacyItemRequest;
import com.apexcare.billing.dto.AdjustStockRequest;
import com.apexcare.billing.dto.ApiResponse;
import com.apexcare.billing.dto.DispenseRequest;
import com.apexcare.billing.dto.PharmacyItemResponse;
import com.apexcare.billing.dto.RestockRequest;
import com.apexcare.billing.entity.PharmacyItem;
import com.apexcare.billing.security.JwtPrincipal;
import com.apexcare.billing.service.PharmacyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/billing/pharmacy")
@Tag(name = "Pharmacy & Medicine Inventory",
     description = "Medicine inventory, stock operations, and dispensing")
@SecurityRequirement(name = "bearerAuth")
public class PharmacyController {

    private final PharmacyService pharmacyService;

    public PharmacyController(PharmacyService pharmacyService) {
        this.pharmacyService = pharmacyService;
    }

    // =================================================================
    // READ
    // =================================================================

    @GetMapping("/inventory")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST', 'DOCTOR', 'NURSE', 'RECEPTIONIST', 'BILLING_ADMIN')")
    @Operation(summary = "Paginated medicine inventory; filter by category or query string")
    public ResponseEntity<ApiResponse<Page<PharmacyItemResponse>>> getInventory(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "medicineName", direction = Sort.Direction.ASC) Pageable pageable,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        Page<PharmacyItem> page = pharmacyService.getInventory(category, search, pageable, principal);
        return ResponseEntity.ok(ApiResponse.ok("Inventory fetched",
                page.map(PharmacyItemResponse::from)));
    }

    @GetMapping("/inventory/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST', 'DOCTOR', 'NURSE', 'RECEPTIONIST', 'BILLING_ADMIN')")
    @Operation(summary = "Get a single medicine record by ID")
    public ResponseEntity<ApiResponse<PharmacyItemResponse>> getItem(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        PharmacyItem item = pharmacyService.getItem(id, principal);
        return ResponseEntity.ok(ApiResponse.ok("Item fetched", PharmacyItemResponse.from(item)));
    }

    @GetMapping({"/low-stock", "/alerts/low-stock"})
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST', 'DOCTOR', 'NURSE', 'RECEPTIONIST', 'BILLING_ADMIN')")
    @Operation(summary = "List medicines whose quantity is at or below their reorder threshold")
    public ResponseEntity<ApiResponse<List<PharmacyItemResponse>>> getLowStock(
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        List<PharmacyItemResponse> items = pharmacyService.getLowStockItems(principal)
                .stream()
                .map(PharmacyItemResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Low stock items retrieved", items));
    }

    @GetMapping({"/expiring-soon", "/alerts/expiring", "/expiring"})
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST', 'DOCTOR', 'NURSE', 'RECEPTIONIST', 'BILLING_ADMIN')")
    @Operation(summary = "List medicines expiring within N days (default 30)")
    public ResponseEntity<ApiResponse<List<PharmacyItemResponse>>> getExpiringSoon(
            @RequestParam(defaultValue = "30") @Min(1) int days,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        List<PharmacyItemResponse> items = pharmacyService.getExpiringSoon(days, principal)
                .stream()
                .map(PharmacyItemResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Expiring items retrieved", items));
    }

    // =================================================================
    // WRITE — catalog / stock operations
    // =================================================================

    @PostMapping("/inventory")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST', 'DOCTOR', 'BILLING_ADMIN')")
    @Operation(
            summary = "Add a new medicine batch or update an existing one (ADMIN, PHARMACIST, DOCTOR, BILLING_ADMIN)",
            description = "Upserts based on (medicineName, strength, batchNumber). "
                        + "Unit price, threshold, and expiry are updated if already present."
    )
    public ResponseEntity<ApiResponse<PharmacyItemResponse>> addOrUpdate(
            @RequestBody AddPharmacyItemRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        PharmacyItem saved = pharmacyService.addOrUpdateItem(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Item saved", PharmacyItemResponse.from(saved)));
    }

    @PostMapping("/dispense")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST', 'DOCTOR', 'NURSE', 'BILLING_ADMIN')")
    @Operation(
            summary = "Dispense quantity of a medicine to a patient",
            description = "Decrements stock quantity; fails if quantity requested exceeds stock or if expired. Auto-attaches to patient bill."
    )
    public ResponseEntity<ApiResponse<PharmacyItemResponse>> dispense(
            @RequestBody DispenseRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        PharmacyItem updated = pharmacyService.dispenseMedicine(
                request.itemId(),
                request.quantity(),
                request.patientId(),
                request.patientName(),
                request.autoAddToBill(),
                principal
        );
        return ResponseEntity.ok(ApiResponse.ok("Medicine dispensed",
                PharmacyItemResponse.from(updated)));
    }

    @PostMapping("/restock")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST', 'DOCTOR', 'NURSE', 'BILLING_ADMIN')")
    @Operation(
            summary = "Restock existing batch (ADMIN, PHARMACIST, DOCTOR, NURSE, BILLING_ADMIN)",
            description = "Adds quantity to current stock; fails if quantity is zero or negative."
    )
    public ResponseEntity<ApiResponse<PharmacyItemResponse>> restock(
            @RequestBody RestockRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        PharmacyItem updated = pharmacyService.restockMedicine(
                request.itemId(), request.quantity(), principal);
        return ResponseEntity.ok(ApiResponse.ok("Stock replenished",
                PharmacyItemResponse.from(updated)));
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Adjust stock to an exact value (ADMIN only)",
            description = "Used for stock-take reconciliation. Every adjustment is audited."
    )
    public ResponseEntity<ApiResponse<PharmacyItemResponse>> adjust(
            @RequestBody AdjustStockRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        PharmacyItem updated = pharmacyService.adjustStock(
                request.itemId(), request.newQuantity(), request.reason(), principal);
        return ResponseEntity.ok(ApiResponse.ok("Stock adjusted",
                PharmacyItemResponse.from(updated)));
    }
}
