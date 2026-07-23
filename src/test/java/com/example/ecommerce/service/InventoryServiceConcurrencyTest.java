package com.example.ecommerce.service;

import com.example.ecommerce.domain.Category;
import com.example.ecommerce.domain.Product;
import com.example.ecommerce.exception.InsufficientStockException;
import com.example.ecommerce.repository.CategoryRepository;
import com.example.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.kafka.listener.auto-startup=false",
    "spring.jpa.show-sql=false"
})
class InventoryServiceConcurrencyTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Long productId;

    @BeforeEach
    void seedProduct() {
        Category category = categoryRepository.save(
            new Category("Electronics-" + UUID.randomUUID()));
        Product product = productRepository.save(new Product(
            "Concurrency Widget", "Hot SKU",
            new BigDecimal("9.99"), 1, category, "SKU-CONC-" + UUID.randomUUID()));
        productId = product.getId();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reserveStock_shouldAllowOnlyOneReservation_whenTwoThreadsCompeteForLastUnit()
            throws InterruptedException {
        int threadCount = 2;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        List<Throwable> unexpected = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startGate.await();
                    inventoryService.reserveStock(productId, 1);
                    successes.incrementAndGet();
                } catch (InsufficientStockException e) {
                    insufficient.incrementAndGet();
                } catch (Throwable t) {
                    synchronized (unexpected) {
                        unexpected.add(t);
                    }
                } finally {
                    done.countDown();
                }
            }).start();
        }

        startGate.countDown();
        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        assertThat(unexpected).isEmpty();
        assertThat(successes.get()).isEqualTo(1);
        assertThat(insufficient.get()).isEqualTo(1);

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStockQuantity()).isZero();
        assertThat(product.getVersion()).isGreaterThan(0L);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reserveStock_shouldReserveBothUnits_whenStockIsTwo() throws InterruptedException {
        Product product = productRepository.findById(productId).orElseThrow();
        product.setStockQuantity(2);
        productRepository.save(product);

        int threadCount = 2;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger();
        List<Throwable> failures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startGate.await();
                    inventoryService.reserveStock(productId, 1);
                    successes.incrementAndGet();
                } catch (Throwable t) {
                    synchronized (failures) {
                        failures.add(t);
                    }
                } finally {
                    done.countDown();
                }
            }).start();
        }

        startGate.countDown();
        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        assertThat(failures).isEmpty();
        assertThat(successes.get()).isEqualTo(2);

        Product updated = productRepository.findById(productId).orElseThrow();
        assertThat(updated.getStockQuantity()).isZero();
    }
}
