package com.dpdk.core.service;

import com.dpdk.core.model.dto.AutoScanCreateRequest;
import com.dpdk.core.model.dto.AutoScanResultVO;
import com.dpdk.core.model.entity.ParseTask;

public interface AutoScanService {
    AutoScanResultVO scanCandidates();

    ParseTask createTaskFromScan(AutoScanCreateRequest request);
}
