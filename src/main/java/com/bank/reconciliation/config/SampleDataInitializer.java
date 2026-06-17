package com.bank.reconciliation.config;

import com.bank.reconciliation.entity.BankOrder;
import com.bank.reconciliation.repository.BankOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@Profile("default")
public class SampleDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataInitializer.class);

    private final BankOrderRepository bankOrderRepository;

    @Autowired
    public SampleDataInitializer(BankOrderRepository bankOrderRepository) {
        this.bankOrderRepository = bankOrderRepository;
    }

    @Override
    public void run(String... args) {
        if (bankOrderRepository.count() > 0) {
            log.info("已存在测试数据，跳过初始化");
            return;
        }

        log.info("开始初始化测试数据");
        LocalDateTime today = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime yesterday = today.minusDays(1);

        List<BankOrder> orders = new ArrayList<>();
        Random random = new Random();

        for (int i = 1; i <= 100; i++) {
            String orderNo = "ORD" + String.format("%08d", i);
            String traceNo = "TRACE" + String.format("%08d", i);
            BigDecimal amount = BigDecimal.valueOf(100 + random.nextInt(10000) / 100.0 * 100).setScale(2, RoundingMode.HALF_UP);

            BankOrder order = new BankOrder();
            order.setOrderNo(orderNo);
            order.setUnionpayTraceNo(traceNo);
            order.setAmount(amount);
            order.setCurrency("CNY");
            order.setStatus("SUCCESS");
            order.setPayerAccount("622202******1234");
            order.setPayeeAccount("622202******5678");
            order.setRemark("测试订单" + i);
            order.setTransDate(yesterday.plusMinutes(i * 10L));
            orders.add(order);
        }

        for (int i = 101; i <= 110; i++) {
            String orderNo = "ORD" + String.format("%08d", i);
            BankOrder order = new BankOrder();
            order.setOrderNo(orderNo);
            order.setUnionpayTraceNo(null);
            order.setAmount(BigDecimal.valueOf(500 + i * 10).setScale(2, RoundingMode.HALF_UP));
            order.setCurrency("CNY");
            order.setStatus("SUCCESS");
            order.setPayerAccount("622202******1234");
            order.setPayeeAccount("622202******5678");
            order.setRemark("本地独有订单" + i);
            order.setTransDate(yesterday.plusMinutes(i * 10L));
            orders.add(order);
        }

        bankOrderRepository.saveAll(orders);
        log.info("测试数据初始化完成，共 {} 条订单", orders.size());
    }
}
