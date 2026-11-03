package com.apexcare.billing.service;

import com.apexcare.billing.dto.AddPharmacyItemRequest;
import com.apexcare.billing.entity.Bill;
import com.apexcare.billing.entity.DosageForm;
import com.apexcare.billing.entity.MedicineCategory;
import com.apexcare.billing.entity.PatientDispenseRecord;
import com.apexcare.billing.entity.PaymentStatus;
import com.apexcare.billing.entity.PharmacyItem;
import com.apexcare.billing.entity.Role;
import com.apexcare.billing.exception.AuthorizationException;
import com.apexcare.billing.exception.InvalidRequestException;
import com.apexcare.billing.exception.ResourceNotFoundException;
import com.apexcare.billing.repository.BillRepository;
import com.apexcare.billing.repository.PatientDispenseRecordRepository;
import com.apexcare.billing.repository.PharmacyItemRepository;
import com.apexcare.billing.security.JwtPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PharmacyService {

    private static final Logger log = LoggerFactory.getLogger(PharmacyService.class);

    private final PharmacyItemRepository repository;
    private final AuditLogService auditLogService;
    private final PatientDispenseRecordRepository patientDispenseRecordRepository;
    private final BillRepository billRepository;

    public PharmacyService(PharmacyItemRepository repository,
                           AuditLogService auditLogService,
                           PatientDispenseRecordRepository patientDispenseRecordRepository,
                           BillRepository billRepository) {
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.patientDispenseRecordRepository = patientDispenseRecordRepository;
        this.billRepository = billRepository;
    }

    // =================================================================
    // READ
    // =================================================================

    @Transactional(readOnly = true)
    public Page<PharmacyItem> getInventory(String category,
                                           String search,
                                           Pageable pageable,
                                           JwtPrincipal principal) {
        requireStaff(principal);

        if (search != null && !search.isBlank()) {
            return repository.searchByNameOrGeneric(search.trim(), pageable);
        }
        if (category != null && !category.isBlank()) {
            return repository.findByCategory(parseCategory(category), pageable);
        }
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public PharmacyItem getItem(UUID id, JwtPrincipal principal) {
        requireStaff(principal);
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("PharmacyItem", id));
    }

    @Transactional(readOnly = true)
    public List<PharmacyItem> getLowStockItems(JwtPrincipal principal) {
        requireStaff(principal);
        return repository.findLowStockItems();
    }

    @Transactional(readOnly = true)
    public List<PharmacyItem> getExpiringSoon(int days, JwtPrincipal principal) {
        requireStaff(principal);
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(days);
        return repository.findByExpiryDateBetweenOrderByExpiryDateAsc(today, cutoff);
    }

    // =================================================================
    // WRITE — inventory management
    // =================================================================

    @Transactional
    public PharmacyItem addOrUpdateItem(AddPharmacyItemRequest request, JwtPrincipal principal) {
        requirePharmacistOrAdmin(principal);

        String batchNo = (request.batchNumber() != null && !request.batchNumber().isBlank())
                ? request.batchNumber().trim()
                : "B-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Same medicine + strength + batch = same physical batch → update existing
        PharmacyItem item = repository
                .findFirstByMedicineNameAndStrengthAndBatchNumber(
                        request.medicineName(), request.strength(), batchNo)
                .orElseGet(PharmacyItem::new);

        boolean isNew = item.getId() == null;

        if (isNew) {
            item.setMedicineName(request.medicineName());
            item.setGenericName(request.genericName());
            item.setCategory(parseCategory(request.category()));
            item.setDosageForm(parseDosageForm(request.dosageForm()));
            item.setStrength(request.strength());
            item.setBatchNumber(batchNo);
            item.setManufacturer(request.manufacturer());
            item.setCreatedBy(principal.userId());
            item.setStockQuantity(request.stockQuantity() != null ? request.stockQuantity() : 0);
        }

        // These are always updatable
        item.setUnitPrice(request.unitPrice());
        item.setReorderThreshold(request.reorderThreshold() != null
                ? request.reorderThreshold() : 20);
        item.setExpiryDate(request.expiryDate());

        PharmacyItem saved = repository.save(item);

        auditLogService.record(principal,
                isNew ? "ADD_PHARMACY_ITEM" : "UPDATE_PHARMACY_ITEM",
                "PHARMACY_ITEM", saved.getId().toString(),
                (isNew ? "Added" : "Updated") + " " + saved.getMedicineName()
                        + " (" + saved.getStrength() + ") batch=" + saved.getBatchNumber(),
                null);

        return saved;
    }

    // =================================================================
    // WRITE — stock operations
    // =================================================================

    @Transactional
    public PharmacyItem dispenseMedicine(UUID itemId, int quantity, JwtPrincipal principal) {
        return dispenseMedicine(itemId, quantity, null, null, false, principal);
    }

    @Transactional
    public PharmacyItem dispenseMedicine(UUID itemId,
                                         int quantity,
                                         UUID patientId,
                                         String patientName,
                                         Boolean autoAddToBill,
                                         JwtPrincipal principal) {
        requireClinicalOrPharmacy(principal);

        if (quantity <= 0) {
            throw new InvalidRequestException("Quantity must be greater than zero");
        }

        PharmacyItem item = repository.findById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("PharmacyItem", itemId));

        if (item.isExpired()) {
            throw new InvalidRequestException(
                    "Cannot dispense expired medicine: " + item.getMedicineName()
                            + " (expired " + item.getExpiryDate() + ")");
        }

        item.dispense(quantity);   // throws if insufficient; @Version guards race
        PharmacyItem saved = repository.save(item);

        // Record patient dispensing and auto-add to active bill if available
        if (patientId != null) {
            BigDecimal totalPrice = saved.getUnitPrice().multiply(BigDecimal.valueOf(quantity));
            PatientDispenseRecord record = new PatientDispenseRecord();
            record.setPatientId(patientId);
            record.setPatientName(patientName != null && !patientName.isBlank() ? patientName : "Patient");
            record.setPharmacyItemId(saved.getId());
            record.setMedicineName(saved.getMedicineName() + " (" + saved.getStrength() + ")");
            record.setQuantity(quantity);
            record.setUnitPrice(saved.getUnitPrice());
            record.setTotalAmount(totalPrice);
            record.setDispensedBy(principal != null ? principal.userId() : null);

            boolean addToBill = autoAddToBill == null || autoAddToBill;
            if (addToBill) {
                Optional<Bill> activeBill = billRepository
                        .findFirstByPatientIdAndPaymentStatusOrderByCreatedAtDesc(patientId, PaymentStatus.PENDING);
                if (activeBill.isPresent()) {
                    Bill bill = activeBill.get();
                    BigDecimal currentMeds = bill.getMedicineCharges() != null ? bill.getMedicineCharges() : BigDecimal.ZERO;
                    bill.setMedicineCharges(currentMeds.add(totalPrice));
                    String noteAppend = "+ Dispensed: " + quantity + "x " + saved.getMedicineName();
                    bill.setNotes(bill.getNotes() != null && !bill.getNotes().isBlank()
                            ? bill.getNotes() + "; " + noteAppend
                            : noteAppend);
                    billRepository.save(bill);

                    record.setBilled(true);
                    record.setBillId(bill.getId());
                    log.info("Auto-added dispensed medicine charge {} to active bill {}",
                            totalPrice, bill.getId());
                }
            }
            patientDispenseRecordRepository.save(record);
        }

        auditLogService.record(principal, "DISPENSE_MEDICINE", "PHARMACY_ITEM",
                saved.getId().toString(),
                "Dispensed " + quantity + " of " + saved.getMedicineName()
                        + (patientId != null ? " to patient " + patientId : "")
                        + " (remaining " + saved.getStockQuantity() + ")",
                null);

        if (saved.isLowStock()) {
            log.warn("LOW STOCK itemId={} name={} remaining={} threshold={}",
                    saved.getId(), saved.getMedicineName(),
                    saved.getStockQuantity(), saved.getReorderThreshold());
        }

        return saved;
    }

    @Transactional
    public PharmacyItem restockMedicine(UUID itemId, int quantity, JwtPrincipal principal) {
        requireClinicalOrPharmacy(principal);

        if (quantity <= 0) {
            throw new InvalidRequestException("Quantity must be greater than zero");
        }

        PharmacyItem item = repository.findById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("PharmacyItem", itemId));

        item.replenish(quantity);
        PharmacyItem saved = repository.save(item);

        auditLogService.record(principal, "REPLENISH_STOCK", "PHARMACY_ITEM",
                saved.getId().toString(),
                "Restocked " + quantity + " of " + saved.getMedicineName()
                        + " (new total " + saved.getStockQuantity() + ")",
                null);

        return saved;
    }

    @Transactional
    public PharmacyItem adjustStock(UUID itemId, int newQuantity, String reason, JwtPrincipal principal) {
        requireAdmin(principal);

        if (newQuantity < 0) {
            throw new InvalidRequestException("newQuantity cannot be negative");
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("reason is required for stock adjustments");
        }

        PharmacyItem item = repository.findById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of("PharmacyItem", itemId));

        int previous = item.getStockQuantity();
        item.adjustTo(newQuantity);
        PharmacyItem saved = repository.save(item);

        auditLogService.record(principal, "ADJUST_STOCK", "PHARMACY_ITEM",
                saved.getId().toString(),
                "Adjusted " + saved.getMedicineName() + " from "
                        + previous + " to " + newQuantity + " — reason: " + reason,
                null);

        return saved;
    }

    // =================================================================
    // Category & Dosage parsing helpers
    // =================================================================

    private static MedicineCategory parseCategory(String val) {
        if (val == null || val.isBlank()) {
            return MedicineCategory.OTHER;
        }
        String clean = val.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        if (clean.equals("ANTIBIOTICS")) return MedicineCategory.ANTIBIOTIC;
        if (clean.contains("VITAMIN") || clean.contains("SUPPLEMENT")) return MedicineCategory.VITAMIN_SUPPLEMENT;
        if (clean.contains("DERMAT") || clean.contains("TOPICAL") || clean.contains("CREAM") || clean.contains("OINTMENT")) return MedicineCategory.TOPICAL;
        if (clean.contains("EYE") || clean.contains("EAR") || clean.contains("OPHTHALMIC")) return MedicineCategory.OPHTHALMIC;
        if (clean.contains("CARDIO")) return MedicineCategory.CARDIOVASCULAR;
        if (clean.contains("GASTRO")) return MedicineCategory.GASTROINTESTINAL;
        if (clean.contains("RESPIRAT")) return MedicineCategory.RESPIRATORY;
        if (clean.contains("HISTAMIN") || clean.contains("ALLERG")) return MedicineCategory.ANTIHISTAMINE;
        if (clean.contains("DIABET")) return MedicineCategory.ANTIDIABETIC;
        if (clean.contains("PAIN") || clean.contains("ANALGESIC")) return MedicineCategory.ANALGESIC;
        if (clean.contains("FEVER") || clean.contains("ANTIPYRETIC")) return MedicineCategory.ANTIPYRETIC;
        try {
            return MedicineCategory.valueOf(clean);
        } catch (IllegalArgumentException e) {
            return MedicineCategory.OTHER;
        }
    }

    private static DosageForm parseDosageForm(String val) {
        if (val == null || val.isBlank()) {
            return DosageForm.TABLET;
        }
        String clean = val.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        if (clean.contains("SYRUP") || clean.contains("SUSPENSION") || clean.contains("LIQUID")) return DosageForm.SUSPENSION;
        if (clean.contains("INJECT")) return DosageForm.INJECTION;
        if (clean.contains("CAPSULE")) return DosageForm.CAPSULE;
        if (clean.contains("TAB")) return DosageForm.TABLET;
        if (clean.contains("DROP")) return DosageForm.DROPS;
        if (clean.contains("OINTMENT")) return DosageForm.OINTMENT;
        if (clean.contains("CREAM")) return DosageForm.CREAM;
        if (clean.contains("GEL")) return DosageForm.GEL;
        if (clean.contains("INHAL")) return DosageForm.INHALER;
        try {
            return DosageForm.valueOf(clean);
        } catch (IllegalArgumentException e) {
            return DosageForm.TABLET;
        }
    }

    // =================================================================
    // Authorization helpers
    // =================================================================

    private static void requireAdmin(JwtPrincipal principal) {
        requireAuthenticated(principal);
        if (!principal.hasRole(Role.ADMIN)) {
            throw new AuthorizationException("ADMIN role required");
        }
    }

    private static void requirePharmacistOrAdmin(JwtPrincipal principal) {
        requireAuthenticated(principal);
        if (!principal.hasAnyRole(Role.ADMIN, Role.PHARMACIST, Role.DOCTOR, Role.BILLING_ADMIN)) {
            throw new AuthorizationException(
                    "ADMIN, PHARMACIST, DOCTOR, or BILLING_ADMIN role required");
        }
    }

    private static void requireClinicalOrPharmacy(JwtPrincipal principal) {
        requireAuthenticated(principal);
        if (!principal.hasAnyRole(Role.ADMIN, Role.PHARMACIST, Role.NURSE, Role.DOCTOR, Role.BILLING_ADMIN)) {
            throw new AuthorizationException(
                    "ADMIN, PHARMACIST, NURSE, DOCTOR, or BILLING_ADMIN role required");
        }
    }

    private static void requireStaff(JwtPrincipal principal) {
        requireAuthenticated(principal);
        if (!principal.hasAnyRole(
                Role.ADMIN, Role.PHARMACIST, Role.NURSE,
                Role.DOCTOR, Role.RECEPTIONIST, Role.BILLING_ADMIN)) {
            throw new AuthorizationException("Insufficient role to access pharmacy inventory");
        }
    }

    private static void requireAuthenticated(JwtPrincipal principal) {
        if (principal == null) {
            throw new AuthorizationException("Authentication required");
        }
    }
}
