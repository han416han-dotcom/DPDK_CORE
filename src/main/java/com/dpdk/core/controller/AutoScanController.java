package com.dpdk.core.controller;

import com.dpdk.core.model.dto.AutoScanCreateRequest;
import com.dpdk.core.model.dto.AutoScanResultVO;
import com.dpdk.core.model.entity.ParseTask;
import com.dpdk.core.service.AutoScanService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scan")
public class AutoScanController {

    private final AutoScanService autoScanService;

    public AutoScanController(AutoScanService autoScanService) {
        this.autoScanService = autoScanService;
    }

    @GetMapping("/candidates")
    public ResponseEntity<AutoScanResultVO> scanCandidates() {
        return ResponseEntity.ok(autoScanService.scanCandidates());
    }

    @PostMapping("/create-task")
    public ResponseEntity<ParseTask> createTask(@Valid @RequestBody AutoScanCreateRequest request) {
        return ResponseEntity.ok(autoScanService.createTaskFromScan(request));
    }
}
