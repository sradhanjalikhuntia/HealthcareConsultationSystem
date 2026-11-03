package com.apexcare.billing.service;

import com.apexcare.billing.controller.LabController.CreateLabOrderRequest;
import com.apexcare.billing.controller.LabController.RecordLabResultRequest;
import com.apexcare.billing.entity.Bill;
import com.apexcare.billing.entity.LabTestOrder;
import com.apexcare.billing.entity.PaymentStatus;
import com.apexcare.billing.repository.BillRepository;
import com.apexcare.billing.repository.LabTestOrderRepository;
import com.apexcare.billing.security.JwtPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LabService {

    private static final Logger log = LoggerFactory.getLogger(LabService.class);

    private final LabTestOrderRepository repository;
    private final BillRepository billRepository;

    public LabService(LabTestOrderRepository repository, BillRepository billRepository) {
        this.repository = repository;
        this.billRepository = billRepository;
    }

    public List<LabTestOrder> getAllOrders() {
        return repository.findAll();
    }

    public List<LabTestOrder> getPatientOrders(UUID patientId) {
        return repository.findByPatientIdOrderByOrderedAtDesc(patientId);
    }

    public Page<LabTestOrder> getAllOrders(Pageable pageable, JwtPrincipal principal) {
        return repository.findAll(pageable);
    }

    public List<LabTestOrder> getPatientOrders(UUID patientId, JwtPrincipal principal) {
        if (principal != null && principal.isPatient() && !principal.isLinkedToPatient(patientId)) {
            throw new SecurityException("Patients may only access their own orders");
        }
        return getPatientOrders(patientId);
    }

    public LabTestOrder getOrderById(UUID id, JwtPrincipal principal) {
        LabTestOrder order = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lab test order not found: " + id));
        if (principal != null && principal.isPatient() && !principal.isLinkedToPatient(order.getPatientId())) {
            throw new SecurityException("Patients may only access their own orders");
        }
        return order;
    }

    @Transactional
    public LabTestOrder createOrder(CreateLabOrderRequest request, JwtPrincipal principal) {
        LabTestOrder order = new LabTestOrder();
        order.setPatientId(request.patientId());
        order.setDoctorId(request.doctorId() != null ? request.doctorId()
                : (principal != null && principal.userId() != null ? principal.userId() : UUID.randomUUID()));
        order.setAppointmentId(request.appointmentId());
        order.setTestName(request.testName());
        order.setCategory(request.category() != null ? request.category().trim().toUpperCase() : "BLOOD");
        order.setPriority(request.priority() != null ? request.priority().trim().toUpperCase() : "ROUTINE");
        order.setOrderedBy(principal == null ? null : principal.userId());
        order.setTestPrice(LabTestOrder.determineTestPrice(order.getCategory()));
        return createOrder(order);
    }

    @Transactional
    public LabTestOrder recordResult(UUID id, RecordLabResultRequest request, JwtPrincipal principal) {
        return updateResult(id, request.resultSummary(), request.referenceRange(),
                request.abnormalFlag(), request.resultFileUrl(), principal);
    }

    @Transactional
    public LabTestOrder createOrder(LabTestOrder order) {
        if (order.getStatus() == null) order.setStatus("ORDERED");
        if (order.getTestPrice() == null) {
            order.setTestPrice(LabTestOrder.determineTestPrice(order.getCategory()));
        }
        LabTestOrder saved = repository.save(order);

        // Check if an active pending bill exists for this patient to auto-accumulate
        if (saved.getPatientId() != null) {
            Optional<Bill> activeBill = billRepository
                    .findFirstByPatientIdAndPaymentStatusOrderByCreatedAtDesc(saved.getPatientId(), PaymentStatus.PENDING);
            if (activeBill.isPresent()) {
                Bill bill = activeBill.get();
                bill.setLabCharges(bill.getLabCharges().add(saved.getTestPrice()));
                String noteAppend = "+ Lab: " + saved.getTestName() + " ($" + saved.getTestPrice() + ")";
                bill.setNotes(bill.getNotes() != null && !bill.getNotes().isBlank()
                        ? bill.getNotes() + "; " + noteAppend
                        : noteAppend);
                billRepository.save(bill);

                saved.setBilled(true);
                saved.setBillId(bill.getId());
                saved = repository.save(saved);
                log.info("Auto-added lab charge {} to active bill {}", saved.getTestPrice(), bill.getId());
            }
        }

        return saved;
    }

    @Transactional
    public LabTestOrder updateResult(UUID id, String resultSummary, String referenceRange, String abnormalFlag) {
        return updateResult(id, resultSummary, referenceRange, abnormalFlag, null, null);
    }

    @Transactional
    public LabTestOrder updateResult(UUID id, String resultSummary, String referenceRange,
                                     String abnormalFlag, String resultFileUrl, JwtPrincipal principal) {
        LabTestOrder order = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lab test order not found: " + id));

        UUID reportedBy = principal != null ? principal.userId() : null;

        // Progress status machine cleanly to COMPLETED
        if ("ORDERED".equals(order.getStatus())) {
            String sampleBarcode = "SMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            order.collectSample(sampleBarcode, reportedBy != null ? reportedBy : UUID.randomUUID());
        }
        if ("SAMPLE_COLLECTED".equals(order.getStatus())) {
            order.startAnalysis();
        }
        if ("IN_ANALYSIS".equals(order.getStatus())) {
            order.complete(resultSummary, referenceRange, abnormalFlag, resultFileUrl, reportedBy);
        } else {
            order.setResultSummary(resultSummary);
            order.setReferenceRange(referenceRange != null && !referenceRange.isBlank() ? referenceRange : "Normal");
            order.setAbnormalFlag(abnormalFlag != null ? abnormalFlag : "NORMAL");
            order.setResultFileUrl(resultFileUrl);
            order.setStatus("COMPLETED");
            order.setCompletedAt(Instant.now());
        }
        return repository.save(order);
    }
}
