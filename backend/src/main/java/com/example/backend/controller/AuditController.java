package com.example.backend.controller;

import com.example.backend.service.ComplianceAuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
@CrossOrigin(origins = "http://localhost:5173") // Add this line
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final ComplianceAuditService auditService;

    public AuditController(ComplianceAuditService auditService) {
        this.auditService = auditService;
    }

    @PostMapping("/rules/index")
    public ResponseEntity<String> indexRule(@RequestBody Map<String, String> request) {
        auditService.indexRegulation(request.get("ruleText"), request.get("ruleId"));
        return ResponseEntity.ok("Regulation indexed successfully into pgvector.");
    }

    @GetMapping("/query")
    public ResponseEntity<String> performAudit(@RequestParam String question, @RequestParam String sensorId) {
        String result = auditService.runAudit(question, sensorId);
        return ResponseEntity.ok(result);
    }
}