package com.example.ecommerce.service;

import com.example.ecommerce.domain.Product;
import com.example.ecommerce.exception.InsufficientStockException;
import com.example.ecommerce.repository.ProductRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class InventoryService {

    private static final Logger log =
        LoggerFactory.getLogger(InventoryService.class);

    private final ProductRepository productRepository;

    public InventoryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Reserves stock for an order item.
     * Uses @Version optimistic locking to prevent overselling.
     * Retries up to 3 times if a concurrent update is detected.
     */
    @Transactional
    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public void reserveStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() ->
                new IllegalArgumentException("Product not found: " + productId));

        if (!product.hasStock(quantity)) {
            throw new InsufficientStockException(
                product.getName(), quantity, product.getStockQuantity());
        }

        product.decrementStock(quantity);
        productRepository.save(product);
        // If another transaction updated this product concurrently,
        // Hibernate throws ObjectOptimisticLockingFailureException
        // and @Retryable re-reads and tries again

        log.info("Reserved {} units of product {}", quantity, productId);
    }

    @Transactional
    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100)
    )
    public void releaseStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() ->
                new IllegalArgumentException("Product not found: " + productId));

        product.incrementStock(quantity);
        productRepository.save(product);
        log.info("Released {} units of product {}", quantity, productId);
    }
}
