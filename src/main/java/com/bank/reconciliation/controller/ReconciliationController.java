package com.bank.reconciliation.controller;

import com.bank.reconciliation.common.ApiResponse;
import com.bank.reconciliation.dto.ReconciliationResult;
import com.bank.reconciliation.dto.ReconciliationUploadResponse;
import com.bank.reconciliation.entity.CheckErrorLedger;
import com.bank.reconciliation.entity.ErrorType;
import com.bank.reconciliation.entity.ReconciliationBatch;
import com.bank.reconciliation.repository.ReconciliationBatchRepository;
import com.bank.reconciliation.service.FileParseService;
import com.bank.reconciliation.service.ReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/reconciliation")
public class ReconciliationController {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationController.class);

    private final FileParseService fileParseService;
    private final ReconciliationService reconciliationService;
    private final ReconciliationBatchRepository batchRepository;

    @Autowired
    public ReconciliationController(FileParseService fileParseService,
                                    ReconciliationService reconciliationService,
                                    ReconciliationBatchRepository batchRepository) {
        this.fileParseService = fileParseService;
        this.reconciliationService = reconciliationService;
        this.batchRepository = batchRepository;
    }

    @PostMapping("/upload")
    public ApiResponse<ReconciliationUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("reconciliationDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate reconciliationDate,
            @RequestParam(value = "channel", defaultValue = "UNIONPAY") String channel) {

        log.info("收到对账文件上传: fileName={}, date={}, channel={}",
                file.getOriginalFilename(), reconciliationDate, channel);

        ReconciliationUploadResponse response = fileParseService.uploadAndParse(file, reconciliationDate, channel);
        return ApiResponse.success(response);
    }

    @PostMapping("/start/{batchNo}")
    public ApiResponse<ReconciliationResult> startReconciliation(@PathVariable String batchNo) {
        log.info("触发对账: batchNo={}", batchNo);
        ReconciliationResult result = reconciliationService.startReconciliation(batchNo);
        return ApiResponse.success(result);
    }

    @GetMapping("/result/{batchNo}")
    public ApiResponse<ReconciliationResult> getReconciliationResult(@PathVariable String batchNo) {
        ReconciliationResult result = reconciliationService.getReconciliationResult(batchNo);
        return ApiResponse.success(result);
    }

    @GetMapping("/batch/list")
    public ApiResponse<List<ReconciliationBatch>> listBatches(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(required = false) String channel) {

        List<ReconciliationBatch> batches;
        if (date != null && channel != null) {
            batches = batchRepository.findByReconciliationDateAndChannel(date, channel);
        } else if (date != null) {
            batches = batchRepository.findByReconciliationDate(date);
        } else {
            batches = batchRepository.findAll();
        }
        return ApiResponse.success(batches);
    }

    @GetMapping("/errors/{batchNo}")
    public ApiResponse<List<CheckErrorLedger>> getErrorLedgers(
            @PathVariable String batchNo,
            @RequestParam(required = false) ErrorType errorType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        List<CheckErrorLedger> errors = reconciliationService.getErrorLedgers(batchNo, errorType, page, size);
        return ApiResponse.success(errors);
    }

    @PostMapping("/auto-reconcile/{batchNo}")
    public ApiResponse<String> autoReconcile(@PathVariable String batchNo) {
        reconciliationService.asyncReconcile(batchNo);
        return ApiResponse.success("对账流程已启动: " + batchNo);
    }
}
