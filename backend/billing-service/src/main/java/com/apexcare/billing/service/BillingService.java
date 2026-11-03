package com.apexcare.billing.service;

import com.apexcare.billing.dto.BillRequest;
import com.apexcare.billing.dto.BillResponse;
import com.apexcare.billing.dto.BillingSummaryResponse;
import com.apexcare.billing.dto.GenerateBillRequest;
import com.apexcare.billing.dto.PaymentReceiptResponse;
import com.apexcare.billing.dto.PaymentRequest;
import com.apexcare.billing.dto.UnbilledChargesSummary;
import com.apexcare.billing.dto.WardDischargeChargeRequest;
import com.apexcare.billing.entity.Bill;
import com.apexcare.billing.entity.LabTestOrder;
import com.apexcare.billing.entity.PatientAdmissionRecord;
import com.apexcare.billing.entity.PatientDispenseRecord;
import com.apexcare.billing.entity.Payment;
import com.apexcare.billing.entity.PaymentStatus;
import com.apexcare.billing.entity.Role;
import com.apexcare.billing.exception.AuthorizationException;
import com.apexcare.billing.exception.InvalidRequestException;
import com.apexcare.billing.exception.ResourceNotFoundException;
import com.apexcare.billing.repository.BillRepository;
import com.apexcare.billing.repository.LabTestOrderRepository;
import com.apexcare.billing.repository.PatientAdmissionRecordRepository;
import com.apexcare.billing.repository.PatientDispenseRecordRepository;
import com.apexcare.billing.repository.PaymentRepository;
import com.apexcare.billing.security.JwtPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final PatientDispenseRecordRepository patientDispenseRecordRepository;
    private final LabTestOrderRepository labTestOrderRepository;
    private final PatientAdmissionRecordRepository patientAdmissionRecordRepository;
    private final AuditLogService auditLogService;
    private final BigDecimal defaultConsultationFee;
    private final BigDecimal defaultTaxPercentage;

    public BillingService(
            BillRepository billRepository,
            PaymentRepository paymentRepository,
            PatientDispenseRecordRepository patientDispenseRecordRepository,
            LabTestOrderRepository labTestOrderRepository,
            PatientAdmissionRecordRepository patientAdmissionRecordRepository,
            AuditLogService auditLogService,
            @Value("${apexcare.billing.default-consultation-fee:500.00}")
            BigDecimal defaultConsultationFee,
            @Value("${apexcare.billing.default-tax-percentage:5.00}")
            BigDecimal defaultTaxPercentage
    ) {
        this.billRepository = billRepository;
        this.paymentRepository = paymentRepository;
        this.patientDispenseRecordRepository = patientDispenseRecordRepository;
        this.labTestOrderRepository = labTestOrderRepository;
        this.patientAdmissionRecordRepository = patientAdmissionRecordRepository;
        this.auditLogService = auditLogService;
        this.defaultConsultationFee = defaultConsultationFee;
        this.defaultTaxPercentage = defaultTaxPercentage;
    }

    // =================================================================
    // CREATE
    // =================================================================

    @Transactional
    public BillResponse createBill(BillRequest request, JwtPrincipal principal) {
        requireBillingStaff(principal);

        // Idempotency: if a bill already exists for the appointment, return it
        if (request.getAppointmentId() != null) {
            Optional<Bill> existing = billRepository
                    .findFirstByAppointmentIdOrderByCreatedAtDesc(request.getAppointmentId());
            if (existing.isPresent()) {
                log.info("Bill already exists for appointmentId={}, returning existing",
                        request.getAppointmentId());
                return BillResponse.from(existing.get());
            }
        }

        Bill bill = buildBill(
                request.getPatientId(),
                request.getUniquePatientId(),
                request.getAppointmentId(),
                request.getConsultationFee(),
                request.getMedicineCharges(),
                request.getLabCharges(),
                request.getWardCharges(),
                request.getOtherCharges(),
                request.getDiscountPercentage(),
                request.getTaxPercentage(),
                request.getNotes(),
                "Standard Consultation Bill");

        Bill saved = billRepository.save(bill);

        // If this bill includes unbilled records, mark them as attached
        linkUnbilledRecordsToBill(request.getPatientId(), saved.getId());

        auditLogService.record(principal, "CREATE_BILL", "BILL", saved.getId().toString(),
                "Bill created total=" + saved.getTotalAmount(), null);

        log.info("Bill created billId={} totalAmount={}",
                saved.getId(), saved.getTotalAmount());
        return BillResponse.from(saved);
    }

    @Transactional
    public BillResponse generateBillForAppointment(GenerateBillRequest request,
                                                   JwtPrincipal principal) {
        if (principal == null) {
            throw new AuthorizationException("Authentication required");
        }

        // Idempotency — appointment-service retries must not create duplicates
        Optional<Bill> existing = billRepository
                .findFirstByAppointmentIdOrderByCreatedAtDesc(request.getAppointmentId());
        if (existing.isPresent()) {
            log.info("Bill already generated for appointmentId={}, returning billId={}",
                    request.getAppointmentId(), existing.get().getId());
            return BillResponse.from(existing.get());
        }

        Bill bill = buildBill(
                request.getPatientId(),
                request.getUniquePatientId(),
                request.getAppointmentId(),
                request.getConsultationFee(),
                request.getMedicineCharges(),
                request.getLabCharges(),
                BigDecimal.ZERO,
                request.getOtherCharges(),
                request.getDiscountPercentage(),
                request.getTaxPercentage(),
                request.getNotes(),
                "Auto-generated Consultation Bill");

        Bill saved = billRepository.save(bill);

        linkUnbilledRecordsToBill(request.getPatientId(), saved.getId());

        auditLogService.record(principal, "GENERATE_BILL", "BILL", saved.getId().toString(),
                "Auto-generated bill for appointment " + request.getAppointmentId(), null);

        log.info("Auto-generated bill appointmentId={} billId={} totalAmount={}",
                request.getAppointmentId(), saved.getId(), saved.getTotalAmount());
        return BillResponse.from(saved);
    }

    // =================================================================
    // REAL-TIME WARD & BED CHARGE RECORDING
    // =================================================================

    @Transactional
    public BillResponse recordWardDischarge(WardDischargeChargeRequest req, JwtPrincipal principal) {
        if (req.patientId() == null) {
            throw new InvalidRequestException("patientId is required");
        }

        // 1. Save or update PatientAdmissionRecord in billing database
        PatientAdmissionRecord record = patientAdmissionRecordRepository
                .findByAdmissionId(req.admissionId())
                .orElseGet(PatientAdmissionRecord::new);

        record.setAdmissionId(req.admissionId() != null ? req.admissionId() : UUID.randomUUID());
        record.setPatientId(req.patientId());
        record.setPatientName(req.patientName());
        record.setBedNumber(req.bedNumber() != null ? req.bedNumber() : "BED");
        record.setWardType(req.wardType() != null ? req.wardType() : "GENERAL");
        record.setAdmittedAt(req.admittedAt() != null ? req.admittedAt() : java.time.Instant.now());
        record.setDischargedAt(req.dischargedAt() != null ? req.dischargedAt() : java.time.Instant.now());
        record.setStayDays(req.stayDays() != null ? req.stayDays() : 1);
        record.setDailyRate(req.dailyRate() != null ? req.dailyRate() : BigDecimal.valueOf(1500.00));

        BigDecimal charge = req.totalCharge() != null
                ? req.totalCharge()
                : record.getDailyRate().multiply(BigDecimal.valueOf(record.getStayDays()));
        record.setTotalAmount(charge);
        record.setDischargeSummary(req.dischargeSummary());

        // 2. Real-time reflection onto active patient Bill
        Optional<Bill> activePendingOpt = billRepository
                .findFirstByPatientIdAndPaymentStatusOrderByCreatedAtDesc(req.patientId(), PaymentStatus.PENDING);

        String wardNote = "Bed " + record.getBedNumber() + " (" + record.getWardType() + "): "
                + record.getStayDays() + " day(s) @ $" + record.getDailyRate();

        Bill bill;
        if (activePendingOpt.isPresent()) {
            bill = activePendingOpt.get();
            bill.setWardCharges(nz(bill.getWardCharges()).add(charge));
            String existingNotes = bill.getNotes();
            bill.setNotes(existingNotes != null && !existingNotes.isBlank()
                    ? existingNotes + " | " + wardNote
                    : wardNote);
            log.info("Auto-added ward stay charge {} to active pending bill {}", charge, bill.getId());
        } else {
            bill = new Bill();
            bill.setPatientId(req.patientId());
            bill.setConsultationFee(BigDecimal.ZERO);
            bill.setWardCharges(charge);
            bill.setNotes("Inpatient Discharge: " + wardNote);
            log.info("Created new pending bill with ward charge {} for patient {}", charge, req.patientId());
        }

        bill.recalculateTotals();
        Bill savedBill = billRepository.save(bill);

        record.setBilled(true);
        record.setBillId(savedBill.getId());
        patientAdmissionRecordRepository.save(record);

        auditLogService.record(principal, "WARD_CHARGE_ADDED", "BILL", savedBill.getId().toString(),
                "Attached ward stay " + wardNote + " charge=" + charge + " to bill", null);

        return BillResponse.from(savedBill);
    }

    // =================================================================
    // UNBILLED PATIENT USAGE ACCUMULATION & AUTO-BILLING
    // =================================================================

    @Transactional(readOnly = true)
    public UnbilledChargesSummary getUnbilledCharges(UUID patientId, JwtPrincipal principal) {
        requireStaffOrSelf(patientId, principal);

        UnbilledChargesSummary summary = new UnbilledChargesSummary();
        summary.setPatientId(patientId);

        List<UnbilledChargesSummary.ChargeLineItem> items = new ArrayList<>();

        // 1. Unbilled medicines
        List<PatientDispenseRecord> unbilledMeds = patientDispenseRecordRepository
                .findByPatientIdAndBilledFalseOrderByDispensedAtDesc(patientId);
        BigDecimal medTotal = BigDecimal.ZERO;
        for (PatientDispenseRecord med : unbilledMeds) {
            BigDecimal amt = med.getTotalAmount() != null
                    ? med.getTotalAmount()
                    : med.getUnitPrice().multiply(BigDecimal.valueOf(med.getQuantity()));
            medTotal = medTotal.add(amt);
            items.add(new UnbilledChargesSummary.ChargeLineItem(
                    "MEDICINE",
                    med.getMedicineName() + " (Qty: " + med.getQuantity() + " @ $" + med.getUnitPrice() + ")",
                    amt,
                    med.getDispensedAt() != null ? med.getDispensedAt().toString() : ""
            ));
        }
        summary.setMedicineCharges(medTotal);

        // 2. Unbilled lab tests
        List<LabTestOrder> unbilledLabs = labTestOrderRepository
                .findByPatientIdAndBilledFalseOrderByOrderedAtDesc(patientId);
        BigDecimal labTotal = BigDecimal.ZERO;
        for (LabTestOrder lab : unbilledLabs) {
            BigDecimal amt = lab.getTestPrice() != null
                    ? lab.getTestPrice()
                    : LabTestOrder.determineTestPrice(lab.getCategory());
            labTotal = labTotal.add(amt);
            items.add(new UnbilledChargesSummary.ChargeLineItem(
                    "LAB_TEST",
                    lab.getTestName() + " (" + lab.getCategory() + " Test)",
                    amt,
                    lab.getOrderedAt() != null ? lab.getOrderedAt().toString() : ""
            ));
        }
        summary.setLabCharges(labTotal);

        // 3. Unbilled ward bed stays
        List<PatientAdmissionRecord> unbilledAdmissions = patientAdmissionRecordRepository
                .findByPatientIdAndBilledFalseOrderByDischargedAtDesc(patientId);
        BigDecimal wardTotal = BigDecimal.ZERO;
        for (PatientAdmissionRecord adm : unbilledAdmissions) {
            BigDecimal amt = adm.getTotalAmount() != null
                    ? adm.getTotalAmount()
                    : adm.getDailyRate().multiply(BigDecimal.valueOf(adm.getStayDays()));
            wardTotal = wardTotal.add(amt);
            items.add(new UnbilledChargesSummary.ChargeLineItem(
                    "WARD",
                    "Bed " + adm.getBedNumber() + " (" + adm.getWardType() + ") - "
                            + adm.getStayDays() + " day(s) @ $" + adm.getDailyRate() + "/day",
                    amt,
                    adm.getDischargedAt() != null ? adm.getDischargedAt().toString() : ""
            ));
        }
        summary.setWardCharges(wardTotal);

        // 4. Consultation check: if patient has no paid bills or pending bills, suggest consultation fee
        long pendingBillCount = billRepository.countByPatientIdAndPaymentStatus(patientId, PaymentStatus.PENDING);
        if (pendingBillCount == 0 && items.isEmpty()) {
            summary.setConsultationFee(defaultConsultationFee);
            items.add(new UnbilledChargesSummary.ChargeLineItem(
                    "CONSULTATION",
                    "Doctor Consultation Fee",
                    defaultConsultationFee,
                    LocalDate.now().toString()
            ));
        } else {
            summary.setConsultationFee(BigDecimal.ZERO);
        }

        BigDecimal total = summary.getConsultationFee()
                .add(summary.getMedicineCharges())
                .add(summary.getLabCharges())
                .add(summary.getWardCharges())
                .add(summary.getOtherCharges());
        summary.setTotalCharges(total);
        summary.setItems(items);

        return summary;
    }

    @Transactional
    public BillResponse generateAutoBill(UUID patientId, JwtPrincipal principal) {
        requireBillingStaff(principal);

        UnbilledChargesSummary summary = getUnbilledCharges(patientId, principal);

        // Check if patient already has an open pending bill
        Optional<Bill> existingPending = billRepository
                .findFirstByPatientIdAndPaymentStatusOrderByCreatedAtDesc(patientId, PaymentStatus.PENDING);

        Bill bill;
        if (existingPending.isPresent()) {
            bill = existingPending.get();
            bill.setMedicineCharges(nz(bill.getMedicineCharges()).add(summary.getMedicineCharges()));
            bill.setLabCharges(nz(bill.getLabCharges()).add(summary.getLabCharges()));
            bill.setWardCharges(nz(bill.getWardCharges()).add(summary.getWardCharges()));
            bill.setOtherCharges(nz(bill.getOtherCharges()).add(summary.getOtherCharges()));

            String extraNotes = "Auto-updated from patient usage: " + summary.getItems().size() + " items";
            bill.setNotes(bill.getNotes() != null && !bill.getNotes().isBlank()
                    ? bill.getNotes() + "; " + extraNotes
                    : extraNotes);
        } else {
            bill = new Bill();
            bill.setPatientId(patientId);
            bill.setConsultationFee(summary.getConsultationFee());
            bill.setMedicineCharges(summary.getMedicineCharges());
            bill.setLabCharges(summary.getLabCharges());
            bill.setWardCharges(summary.getWardCharges());
            bill.setOtherCharges(summary.getOtherCharges());
            bill.setDiscountPercentage(BigDecimal.ZERO);
            bill.setTaxPercentage(defaultTaxPercentage);
            bill.setNotes("Auto-generated Consolidated Bill (" + summary.getItems().size() + " patient items)");
        }

        Bill saved = billRepository.save(bill);

        // Mark all unbilled dispense records, lab orders, and ward records as billed
        linkUnbilledRecordsToBill(patientId, saved.getId());

        auditLogService.record(principal, "AUTO_BILL_GENERATED", "BILL", saved.getId().toString(),
                "Auto-generated consolidated bill for patient " + patientId + " total=" + saved.getTotalAmount(), null);

        log.info("Consolidated bill auto-generated billId={} patientId={} totalAmount={}",
                saved.getId(), patientId, saved.getTotalAmount());
        return BillResponse.from(saved);
    }

    private void linkUnbilledRecordsToBill(UUID patientId, UUID billId) {
        List<PatientDispenseRecord> meds = patientDispenseRecordRepository
                .findByPatientIdAndBilledFalseOrderByDispensedAtDesc(patientId);
        for (PatientDispenseRecord med : meds) {
            med.setBilled(true);
            med.setBillId(billId);
        }
        if (!meds.isEmpty()) {
            patientDispenseRecordRepository.saveAll(meds);
        }

        List<LabTestOrder> labs = labTestOrderRepository
                .findByPatientIdAndBilledFalseOrderByOrderedAtDesc(patientId);
        for (LabTestOrder lab : labs) {
            lab.setBilled(true);
            lab.setBillId(billId);
        }
        if (!labs.isEmpty()) {
            labTestOrderRepository.saveAll(labs);
        }

        List<PatientAdmissionRecord> adms = patientAdmissionRecordRepository
                .findByPatientIdAndBilledFalseOrderByDischargedAtDesc(patientId);
        for (PatientAdmissionRecord adm : adms) {
            adm.setBilled(true);
            adm.setBillId(billId);
        }
        if (!adms.isEmpty()) {
            patientAdmissionRecordRepository.saveAll(adms);
        }
    }

    // =================================================================
    // READ
    // =================================================================

    @Transactional(readOnly = true)
    public Page<BillResponse> getBills(UUID patientId,
                                       PaymentStatus status,
                                       Pageable pageable,
                                       JwtPrincipal principal) {
        requireAuthenticated(principal);
        if (principal.hasRole(Role.DOCTOR)) {
            throw new AuthorizationException("Doctors do not have permission to view billing records");
        }

        if (principal.isPatient()) {
            return billRepository
                    .findByPatientIdOrderByCreatedAtDesc(principal.userId(), pageable)
                    .map(BillResponse::from);
        }

        if (patientId != null && status != null) {
            return billRepository
                    .findByPatientIdAndPaymentStatus(patientId, status, pageable)
                    .map(BillResponse::from);
        }
        if (patientId != null) {
            return billRepository
                    .findByPatientId(patientId, pageable)
                    .map(BillResponse::from);
        }
        if (status != null) {
            return billRepository
                    .findByPaymentStatus(status, pageable)
                    .map(BillResponse::from);
        }
        return billRepository.findAll(pageable).map(BillResponse::from);
    }

    @Transactional(readOnly = true)
    public BillResponse getBill(UUID billId, JwtPrincipal principal) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> ResourceNotFoundException.of("Bill", billId));
        assertCanView(bill, principal);
        return BillResponse.from(bill);
    }

    @Transactional(readOnly = true)
    public List<BillResponse> getBillsForPatient(UUID patientId, JwtPrincipal principal) {
        requireAuthenticated(principal);
        if (principal.hasRole(Role.DOCTOR)) {
            throw new AuthorizationException("Doctors do not have permission to view billing records");
        }
        requireStaffOrSelf(patientId, principal);
        return billRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream()
                .map(BillResponse::from)
                .toList();
    }

    // =================================================================
    // PAYMENT
    // =================================================================

    @Transactional
    public PaymentReceiptResponse processPayment(UUID billId,
                                                 PaymentRequest request,
                                                 JwtPrincipal principal) {
        requireAuthenticated(principal);

        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> ResourceNotFoundException.of("Bill", billId));

        if (principal.isPatient() && !principal.isLinkedToPatient(bill.getPatientId())) {
            throw ResourceNotFoundException.of("Bill", billId);
        }

        if (bill.getPaymentStatus() == PaymentStatus.PAID) {
            throw new InvalidRequestException("This bill has already been paid");
        }
        if (bill.getPaymentStatus() == PaymentStatus.REFUNDED) {
            throw new InvalidRequestException("This bill has been refunded and cannot be paid");
        }

        if (request.getAmount().compareTo(bill.getTotalAmount()) < 0) {
            throw new InvalidRequestException(
                    "Payment amount (" + request.getAmount()
                            + ") is less than bill total (" + bill.getTotalAmount() + ")");
        }

        String txnRef = (request.getTransactionReference() != null
                && !request.getTransactionReference().isBlank())
                ? request.getTransactionReference()
                : "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        if (paymentRepository.existsByTransactionReference(txnRef)) {
            throw new InvalidRequestException(
                    "A payment with this transaction reference already exists");
        }

        Payment payment = new Payment();
        payment.setBillId(bill.getId());
        payment.setPatientId(bill.getPatientId());
        payment.setAmount(request.getAmount());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setTransactionReference(txnRef);
        payment.setNotes(request.getNotes());
        payment.setCreatedBy(principal.userId());
        payment.markPaid();

        Payment savedPayment = paymentRepository.save(payment);

        bill.markPaid(request.getPaymentMethod(), txnRef);
        billRepository.save(bill);

        auditLogService.record(principal, "PAYMENT_RECEIVED", "BILL", bill.getId().toString(),
                "Payment " + request.getAmount() + " via " + request.getPaymentMethod()
                        + " txnRef=" + txnRef, null);

        log.info("Payment processed billId={} txnRef={} amount={}",
                bill.getId(), txnRef, request.getAmount());
        return PaymentReceiptResponse.from(bill, savedPayment);
    }

    @Transactional(readOnly = true)
    public PaymentReceiptResponse getReceipt(UUID billId, JwtPrincipal principal) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> ResourceNotFoundException.of("Bill", billId));

        if (principal == null || (principal.isPatient()
                && !principal.isLinkedToPatient(bill.getPatientId()))) {
            throw ResourceNotFoundException.of("Bill", billId);
        }

        if (bill.getPaymentStatus() != PaymentStatus.PAID) {
            throw new InvalidRequestException(
                    "Receipt is available only for PAID bills (current: "
                            + bill.getPaymentStatus() + ")");
        }

        Payment payment = paymentRepository
                .findFirstByBillIdAndStatusOrderByPaymentDateDesc(
                        billId, PaymentStatus.PAID)
                .orElseThrow(() -> new IllegalStateException(
                        "PAID bill has no matching PAID payment record — data inconsistency"));

        return PaymentReceiptResponse.from(bill, payment);
    }

    // =================================================================
    // SUMMARY
    // =================================================================

    @Transactional(readOnly = true)
    public BillingSummaryResponse getSummaryStats(JwtPrincipal principal) {
        requireAdminOrBilling(principal);

        BillingSummaryResponse s = new BillingSummaryResponse();

        s.setTotalBills(billRepository.count());
        s.setPendingBills(billRepository.countByPaymentStatus(PaymentStatus.PENDING));
        s.setPaidBills(billRepository.countByPaymentStatus(PaymentStatus.PAID));
        s.setFailedBills(billRepository.countByPaymentStatus(PaymentStatus.FAILED));
        s.setRefundedBills(billRepository.countByPaymentStatus(PaymentStatus.REFUNDED));

        s.setTotalBilled(nz(billRepository.sumTotalBilledAmount()));
        s.setTotalRevenue(nz(billRepository.sumTotalAmountByStatus(PaymentStatus.PAID)));
        s.setPendingAmount(nz(billRepository.sumTotalAmountByStatus(PaymentStatus.PENDING)));
        s.setRefundedAmount(nz(billRepository.sumTotalAmountByStatus(PaymentStatus.REFUNDED)));

        List<Bill> allBills = billRepository.findAll();
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> trend = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            String dayName = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);

            BigDecimal revenue = BigDecimal.ZERO;
            for (Bill b : allBills) {
                if (b.getPaymentStatus() == PaymentStatus.PAID && b.getCreatedAt() != null) {
                    LocalDate billDate = b.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
                    if (billDate.equals(d)) {
                        revenue = revenue.add(b.getTotalAmount());
                    }
                }
            }

            Map<String, Object> dayData = new HashMap<>();
            dayData.put("name", dayName);
            dayData.put("revenue", revenue);
            trend.add(dayData);
        }
        s.setRevenueTrend(trend);

        return s;
    }

    // =================================================================
    // HELPERS
    // =================================================================

    private Bill buildBill(UUID patientId,
                           String uniquePatientId,
                           UUID appointmentId,
                           BigDecimal consultationFee,
                           BigDecimal medicineCharges,
                           BigDecimal labCharges,
                           BigDecimal wardCharges,
                           BigDecimal otherCharges,
                           BigDecimal discountPercentage,
                           BigDecimal taxPercentage,
                           String notes,
                           String defaultNotes) {
        Bill bill = new Bill();
        bill.setPatientId(patientId);
        bill.setUniquePatientId(uniquePatientId);
        bill.setAppointmentId(appointmentId);

        bill.setConsultationFee(consultationFee != null
                ? consultationFee : defaultConsultationFee);
        bill.setMedicineCharges(medicineCharges != null
                ? medicineCharges : BigDecimal.ZERO);
        bill.setLabCharges(labCharges != null
                ? labCharges : BigDecimal.ZERO);
        bill.setWardCharges(wardCharges != null
                ? wardCharges : BigDecimal.ZERO);
        bill.setOtherCharges(otherCharges != null
                ? otherCharges : BigDecimal.ZERO);
        bill.setDiscountPercentage(discountPercentage != null
                ? discountPercentage : BigDecimal.ZERO);
        bill.setTaxPercentage(taxPercentage != null
                ? taxPercentage : defaultTaxPercentage);
        bill.setNotes(notes != null ? notes : defaultNotes);

        return bill;
    }

    private static void requireAuthenticated(JwtPrincipal principal) {
        if (principal == null) {
            throw new AuthorizationException("Authentication required");
        }
    }

    private static void requireBillingStaff(JwtPrincipal principal) {
        requireAuthenticated(principal);
        if (!principal.hasAnyRole(Role.ADMIN, Role.BILLING_ADMIN)) {
            throw new AuthorizationException(
                    "Only ADMIN or BILLING_ADMIN role can create or modify invoices");
        }
    }

    private static void requireStaff(JwtPrincipal principal) {
        requireAuthenticated(principal);
        if (!principal.hasAnyRole(Role.ADMIN, Role.RECEPTIONIST, Role.BILLING_ADMIN, Role.DOCTOR)) {
            throw new AuthorizationException(
                    "Insufficient role to create bills");
        }
    }

    private static void requireStaffOrSelf(UUID patientId, JwtPrincipal principal) {
        requireAuthenticated(principal);
        if (principal.hasRole(Role.DOCTOR)) {
            throw new AuthorizationException("Doctors do not have permission to view billing records");
        }
        if (principal.isPatient() && !principal.isLinkedToPatient(patientId)) {
            throw new AuthorizationException("Patients may only access their own billing details");
        }
        if (!principal.isPatient() && !principal.hasAnyRole(Role.ADMIN, Role.RECEPTIONIST, Role.BILLING_ADMIN)) {
            throw new AuthorizationException("Insufficient role to access billing records");
        }
    }

    private static void requireAdminOrBilling(JwtPrincipal principal) {
        requireAuthenticated(principal);
        if (!principal.hasAnyRole(Role.ADMIN, Role.BILLING_ADMIN, Role.RECEPTIONIST)) {
            throw new AuthorizationException(
                    "ADMIN or BILLING_ADMIN role required");
        }
    }

    private void assertCanView(Bill bill, JwtPrincipal principal) {
        requireAuthenticated(principal);
        if (principal.hasRole(Role.DOCTOR)) {
            throw new AuthorizationException("Doctors do not have permission to view billing records");
        }
        if (principal.isPatient() && !principal.isLinkedToPatient(bill.getPatientId())) {
            throw ResourceNotFoundException.of("Bill", bill.getId());
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
