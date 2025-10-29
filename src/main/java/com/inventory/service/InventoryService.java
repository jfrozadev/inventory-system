package com.inventory.service;

import com.inventory.api.InventoryDTOs.*;
import com.inventory.model.InventoryEvent;
import com.inventory.model.Product;
import com.inventory.repository.InventoryEventRepository;
import com.inventory.repository.ProductRepository;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryEventRepository eventRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "inventory:";
    private static final int CACHE_TTL_MINUTES = 5;

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "getInventoryFallback")
    @RateLimiter(name = "inventoryService")
    @Bulkhead(name = "inventoryService")
    public InventoryResponse getInventory(String storeId, String productId) {
        log.debug("Query inventory - Store: {}, Product: {}", storeId, productId);

        String cacheKey = buildCacheKey(storeId, productId);
        InventoryData cachedData = getCachedInventory(cacheKey);

        if (cachedData != null) {
            log.debug("Cache HIT - Key: {}", cacheKey);
            return InventoryResponse.success(
                    "Retrieved from cache",
                    cachedData.toBuilder().cached(true).build()
            );
        }

        log.debug("Cache MISS - Querying database");
        return productRepository.findByStoreIdAndProductId(storeId, productId)
                .map(product -> {
                    InventoryData data = buildInventoryData(product, false);
                    setCachedInventory(cacheKey, data);
                    return InventoryResponse.success("Retrieved from database", data);
                })
                .orElse(InventoryResponse.error("Product not found in inventory"));
    }

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "getInventoryFallback")
    @Bulkhead(name = "inventoryService")
    @Transactional
    public InventoryResponse processSale(SellRequest request) {
        String eventId = UUID.randomUUID().toString();
        log.info("Processing SALE - Store: {}, Product: {}, Qty: {}, EventId: {}",
                request.getStoreId(), request.getProductId(), request.getQuantity(), eventId);

        if (eventRepository.existsByEventId(eventId)) {
            return InventoryResponse.error("Event already processed");
        }

        try {
            Product product = productRepository
                    .findByStoreIdAndProductIdWithLock(request.getStoreId(), request.getProductId())
                    .orElseGet(() -> createNewProduct(request.getStoreId(), request.getProductId()));

            int quantityBefore = product.getQuantity();

            if (product.getQuantity() < request.getQuantity()) {
                logEvent(eventId, request, "SALE", "FAILED", quantityBefore, product.getQuantity());
                return InventoryResponse.error(
                        String.format("Insufficient stock. Available: %d, Requested: %d",
                                product.getQuantity(), request.getQuantity())
                );
            }

            product.removeQuantity(request.getQuantity());
            product.setLastUpdated(LocalDateTime.now());
            productRepository.save(product);

            logEvent(eventId, request, "SALE", "SUCCESS", quantityBefore, product.getQuantity());
            invalidateCache(request.getStoreId(), request.getProductId());

            InventoryData data = buildInventoryData(product, false);
            data.setQuantityBefore(quantityBefore);
            data.setEventId(eventId);

            log.info("SALE completed - Product: {}, Before: {}, After: {}",
                    request.getProductId(), quantityBefore, product.getQuantity());

            return InventoryResponse.success("Sale processed successfully", data);

        } catch (Exception e) {
            log.error("Error processing sale", e);
            logEvent(eventId, request, "SALE", "FAILED", 0, 0);
            throw e;
        }
    }

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "getInventoryFallback")
    @Bulkhead(name = "inventoryService")
    @Transactional
    public InventoryResponse processRestock(RestockRequest request) {
        String eventId = UUID.randomUUID().toString();
        log.info("Processing RESTOCK - Store: {}, Product: {}, Qty: {}",
                request.getStoreId(), request.getProductId(), request.getQuantity());

        try {
            Product product = productRepository
                    .findByStoreIdAndProductIdWithLock(request.getStoreId(), request.getProductId())
                    .orElseGet(() -> createNewProduct(request.getStoreId(), request.getProductId()));

            int quantityBefore = product.getQuantity();

            product.addQuantity(request.getQuantity());
            product.setLastUpdated(LocalDateTime.now());
            productRepository.save(product);

            logEvent(eventId, request, "RESTOCK", "SUCCESS", quantityBefore, product.getQuantity());
            invalidateCache(request.getStoreId(), request.getProductId());

            InventoryData data = buildInventoryData(product, false);
            data.setQuantityBefore(quantityBefore);
            data.setEventId(eventId);

            log.info("RESTOCK completed - Product: {}, Before: {}, After: {}",
                    request.getProductId(), quantityBefore, product.getQuantity());

            return InventoryResponse.success("Restock processed successfully", data);

        } catch (Exception e) {
            log.error("Error processing restock", e);
            throw e;
        }
    }

    private InventoryData getCachedInventory(String cacheKey) {
        try {
            return (InventoryData) redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("Redis GET failed, continuing without cache: {}", e.getMessage());
            return null;
        }
    }

    private void setCachedInventory(String key, InventoryData data) {
        try {
            redisTemplate.opsForValue().set(key, data, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("Cache SET - Key: {}, TTL: {}min", key, CACHE_TTL_MINUTES);
        } catch (Exception e) {
            log.warn("Cache write failed (non-fatal) - Key: {}, Error: {}", key, e.getMessage());
        }
    }

    private void invalidateCache(String storeId, String productId) {
        try {
            String key = buildCacheKey(storeId, productId);
            redisTemplate.delete(key);
            log.debug("Cache INVALIDATED - Key: {}", key);
        } catch (Exception e) {
            log.warn("Cache invalidation failed (non-fatal) - Store: {}, Product: {}, Error: {}",
                    storeId, productId, e.getMessage());
        }
    }

    private String buildCacheKey(String storeId, String productId) {
        return CACHE_PREFIX + storeId + ":" + productId;
    }

    private Product createNewProduct(String storeId, String productId) {
        return Product.builder()
                .storeId(storeId)
                .productId(productId)
                .quantity(0)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    private InventoryData buildInventoryData(Product product, boolean cached) {
        return InventoryData.builder()
                .storeId(product.getStoreId())
                .productId(product.getProductId())
                .quantity(product.getQuantity())
                .lastUpdated(product.getLastUpdated())
                .cached(cached)
                .build();
    }

    private void logEvent(String eventId, Object request,
                          String eventType, String status,
                          int quantityBefore, int quantityAfter) {
        try {
            String storeId = "";
            String productId = "";
            int quantityDelta = 0;

            if (request instanceof SellRequest) {
                SellRequest sell = (SellRequest) request;
                storeId = sell.getStoreId();
                productId = sell.getProductId();
                quantityDelta = -sell.getQuantity();
            } else if (request instanceof RestockRequest) {
                RestockRequest restock = (RestockRequest) request;
                storeId = restock.getStoreId();
                productId = restock.getProductId();
                quantityDelta = restock.getQuantity();
            }

            InventoryEvent event = InventoryEvent.builder()
                    .eventId(eventId)
                    .storeId(storeId)
                    .productId(productId)
                    .eventType(eventType)
                    .status(status)
                    .quantityBefore(quantityBefore)
                    .quantityAfter(quantityAfter)
                    .quantityDelta(quantityDelta)
                    .timestamp(LocalDateTime.now())
                    .build();

            eventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to log event", e);
        }
    }

    @Transactional
    public SyncResponse processBatchSync(SyncRequest request) {
            log.info("Batch sync - Store: {}, Operations: {}",
                    request.getStoreId(), request.getOperations().size());

            if (request.getOperations() == null || request.getOperations().isEmpty()) {
                log.warn("Batch sync received empty operations list - Store: {}", request.getStoreId());
                return SyncResponse.builder()
                        .success(false)
                        .message("Operations list is empty")
                        .successCount(0)
                        .failureCount(1)
                        .errors(List.of("Empty operations"))
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            int successCount = 0;
            int failureCount = 0;
            List<String> errors = new ArrayList<>();

        for (BatchOperation op : request.getOperations()) {
            try {
                if ("SALE".equals(op.getType())) {
                    SellRequest sellReq = SellRequest.builder()
                            .storeId(request.getStoreId())
                            .productId(op.getProductId())
                            .quantity(Math.abs(op.getDelta()))
                            .timestamp(request.getTimestamp())
                            .build();
                    InventoryResponse response = processSale(sellReq);
                    if (response.isSuccess()) {
                        successCount++;
                    } else {
                        failureCount++;
                        errors.add(response.getMessage());
                    }
                } else if ("RESTOCK".equals(op.getType())) {
                    RestockRequest restockReq = RestockRequest.builder()
                            .storeId(request.getStoreId())
                            .productId(op.getProductId())
                            .quantity(op.getDelta())
                            .timestamp(request.getTimestamp())
                            .build();
                    InventoryResponse response = processRestock(restockReq);
                    if (response.isSuccess()) {
                        successCount++;
                    } else {
                        failureCount++;
                        errors.add(response.getMessage());
                    }
                }
            } catch (Exception e) {
                failureCount++;
                errors.add(String.format("Product %s: %s", op.getProductId(), e.getMessage()));
                log.error("Batch operation error", e);
            }
        }

        return SyncResponse.builder()
                .success(failureCount == 0)
                .message(String.format("Batch completed - Success: %d, Failed: %d",
                        successCount, failureCount))
                .successCount(successCount)
                .failureCount(failureCount)
                .errors(errors)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private InventoryResponse getInventoryFallback(String storeId, String productId, Exception ex) {
        log.error("Circuit breaker activated for getInventory - Store: {}, Product: {}, Error: {}",
                storeId, productId, ex.getMessage());
        return InventoryResponse.error("Service temporarily unavailable. Please try again later.");
    }

    private InventoryResponse processSaleFallback(SellRequest request, Exception ex) {
        log.error("Circuit breaker activated for processSale - Store: {}, Product: {}, Error: {}",
                request.getStoreId(), request.getProductId(), ex.getMessage());
        return InventoryResponse.error("Sale service temporarily unavailable. Please try again later.");
    }

    private InventoryResponse processRestockFallback(RestockRequest request, Exception ex) {
        log.error("Circuit breaker activated for processRestock - Store: {}, Product: {}, Error: {}",
                request.getStoreId(), request.getProductId(), ex.getMessage());
        return InventoryResponse.error("Restock service temporarily unavailable. Please try again later.");
    }
}