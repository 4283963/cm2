package com.bank.reconciliation.service;

import com.bank.reconciliation.common.AmountUtils;
import com.bank.reconciliation.common.BusinessException;
import com.bank.reconciliation.dto.ReconciliationUploadResponse;
import com.bank.reconciliation.entity.ReconciliationBatch;
import com.bank.reconciliation.entity.UnionPayTransaction;
import com.bank.reconciliation.repository.ReconciliationBatchRepository;
import com.bank.reconciliation.repository.UnionPayTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FileParseService {

    private static final Logger log = LoggerFactory.getLogger(FileParseService.class);

    private final ReconciliationBatchRepository batchRepository;
    private final UnionPayTransactionRepository unionPayTransactionRepository;

    @Value("${reconciliation.file-storage-path:./reconciliation-files}")
    private String fileStoragePath;

    @Value("${reconciliation.batch-size:1000}")
    private int batchSize;

    @Autowired
    public FileParseService(ReconciliationBatchRepository batchRepository,
                            UnionPayTransactionRepository unionPayTransactionRepository) {
        this.batchRepository = batchRepository;
        this.unionPayTransactionRepository = unionPayTransactionRepository;
    }

    public ReconciliationUploadResponse uploadAndParse(MultipartFile file, LocalDate reconciliationDate, String channel) {
        if (file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String batchNo = generateBatchNo(reconciliationDate, channel);
        String fileName = file.getOriginalFilename();

        Path storagePath = Paths.get(fileStoragePath, channel, reconciliationDate.toString());
        try {
            Files.createDirectories(storagePath);
            Path targetPath = storagePath.resolve(batchNo + "_" + fileName);
            file.transferTo(targetPath.toFile());
            log.info("文件保存成功: {}", targetPath);
        } catch (Exception e) {
            log.error("文件保存失败", e);
            throw new BusinessException("文件保存失败: " + e.getMessage());
        }

        ReconciliationBatch batch = new ReconciliationBatch();
        batch.setBatchNo(batchNo);
        batch.setReconciliationDate(reconciliationDate);
        batch.setChannel(channel);
        batch.setStatus(ReconciliationBatch.Status.PENDING);
        batch.setFileName(fileName);
        batch.setTotalRecords(0L);
        batch.setParsedRecords(0L);
        batch.setStartedAt(LocalDateTime.now());
        batchRepository.save(batch);

        asyncParseFile(batchNo, storagePath.resolve(batchNo + "_" + fileName).toString());

        return new ReconciliationUploadResponse(batchNo, reconciliationDate, channel,
                ReconciliationBatch.Status.PENDING, fileName);
    }

    @Async("fileParseExecutor")
    public void asyncParseFile(String batchNo, String filePath) {
        log.info("开始异步解析文件: batchNo={}, filePath={}", batchNo, filePath);

        ReconciliationBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new BusinessException("批次不存在: " + batchNo));

        try {
            batch.setStatus(ReconciliationBatch.Status.PARSING);
            batch.setStartedAt(LocalDateTime.now());
            batchRepository.save(batch);

            long totalRecords = 0;
            long parsedRecords = 0;
            List<UnionPayTransaction> buffer = new ArrayList<>(batchSize);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {

                String line;
                boolean isFirstLine = true;
                DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                while ((line = reader.readLine()) != null) {
                    totalRecords++;

                    if (isFirstLine) {
                        isFirstLine = false;
                        continue;
                    }

                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    try {
                        UnionPayTransaction transaction = parseLine(batch.getId(), line, dateTimeFormatter);
                        if (transaction != null) {
                            buffer.add(transaction);
                            parsedRecords++;
                        }

                        if (buffer.size() >= batchSize) {
                            saveBatch(buffer);
                            buffer.clear();
                        }
                    } catch (Exception e) {
                        log.warn("解析行失败: line={}, error={}", line, e.getMessage());
                    }
                }

                if (!buffer.isEmpty()) {
                    saveBatch(buffer);
                    buffer.clear();
                }
            }

            batch.setStatus(ReconciliationBatch.Status.PARSED);
            batch.setTotalRecords(totalRecords);
            batch.setParsedRecords(parsedRecords);
            batch.setFinishedAt(LocalDateTime.now());
            batchRepository.save(batch);

            log.info("文件解析完成: batchNo={}, total={}, parsed={}", batchNo, totalRecords, parsedRecords);

        } catch (Exception e) {
            log.error("文件解析失败: batchNo={}", batchNo, e);
            batch.setStatus(ReconciliationBatch.Status.FAILED);
            batch.setFinishedAt(LocalDateTime.now());
            batchRepository.save(batch);
        }
    }

    @Transactional
    protected void saveBatch(List<UnionPayTransaction> transactions) {
        unionPayTransactionRepository.saveAll(transactions);
    }

    private UnionPayTransaction parseLine(Long batchId, String line, DateTimeFormatter dateTimeFormatter) {
        String[] fields = line.split("\\|");
        if (fields.length < 6) {
            return null;
        }

        String traceNo = fields[0].trim();
        String orderNo = fields.length > 1 ? fields[1].trim() : null;
        BigDecimal amount = AmountUtils.of(fields[2].trim());
        String currency = fields.length > 3 ? fields[3].trim() : "CNY";
        String transType = fields.length > 4 ? fields[4].trim() : null;
        String transDateStr = fields.length > 5 ? fields[5].trim() : null;
        String payerAccount = fields.length > 6 ? fields[6].trim() : null;
        String payeeAccount = fields.length > 7 ? fields[7].trim() : null;
        String remark = fields.length > 8 ? fields[8].trim() : null;

        LocalDateTime transDate = null;
        if (transDateStr != null && !transDateStr.isEmpty()) {
            try {
                transDate = LocalDateTime.parse(transDateStr, dateTimeFormatter);
            } catch (Exception e) {
                transDate = LocalDate.parse(transDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay();
            }
        }

        UnionPayTransaction tx = new UnionPayTransaction();
        tx.setBatchId(batchId);
        tx.setTraceNo(traceNo);
        tx.setOrderNo(orderNo);
        tx.setAmount(amount);
        tx.setCurrency(currency);
        tx.setTransType(transType);
        tx.setPayerAccount(payerAccount);
        tx.setPayeeAccount(payeeAccount);
        tx.setRemark(remark);
        tx.setTransDate(transDate);
        return tx;
    }

    private String generateBatchNo(LocalDate date, String channel) {
        String dateStr = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "RECON_" + channel.toUpperCase() + "_" + dateStr + "_" + uuid;
    }
}
