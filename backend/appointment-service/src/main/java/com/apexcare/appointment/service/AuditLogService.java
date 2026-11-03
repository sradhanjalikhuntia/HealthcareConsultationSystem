package com.apexcare.appointment.service;

import com.apexcare.appointment.entity.AuditAction;
import com.apexcare.appointment.entity.ResourceType;
import com.apexcare.appointment.security.JwtPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    public void record(JwtPrincipal principal,
                       AuditAction action,
                       ResourceType resourceType,
                       String resourceId,
                       String description,
                       Object metadata) {
        log.info("audit action={} resourceType={} resourceId={} actor={} description={}",
                action, resourceType, resourceId,
                principal == null ? "anonymous" : principal.userId(), description);
    }
}
