
package com.qaryati.dataquality.controller;

import com.qaryati.dataquality.dto.AuditLogResponse;
import com.qaryati.dataquality.service.AuditLogService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/entity")
    public List<AuditLogResponse> getLogsByEntity(
            @RequestParam String entityType,
            @RequestParam Long entityId
    ) {
        return auditLogService.getLogsByEntity(
                entityType,
                entityId
        );
    }

    @GetMapping("/actor/{actorUserId}")
    public List<AuditLogResponse> getLogsByActor(
            @PathVariable Long actorUserId
    ) {
        return auditLogService.getLogsByActor(actorUserId);
    }

    @GetMapping("/action/{action}")
    public List<AuditLogResponse> getLogsByAction(
            @PathVariable String action
    ) {
        return auditLogService.getLogsByAction(action);
    }
}
