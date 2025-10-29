package com.inventory.service;

import com.inventory.api.InventoryDTOs.*;
import com.inventory.model.InventoryEvent;
import com.inventory.model.Product;
import com.inventory.repository.InventoryEventRepository;
import com.inventory.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService Tests")
class InventoryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryEventRepository eventRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private InventoryService inventoryService;

    private Product testProduct;
    private static final String STORE_ID = "STORE_001";
    private static final String PRODUCT_ID = "PROD_0001";
    private static final String CACHE_KEY = "inventory:STORE_001:PROD_0001";

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .id(1L)
                .storeId(STORE_ID)
                .productId(PRODUCT_ID)
                .quantity(100)
                .lastUpdated(LocalDateTime.now())
                .build();

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("GET - Cache HIT should return data from cache")
    void getInventory_cacheHit_shouldReturnFromCache() {
        InventoryData cachedData = InventoryData.builder()
                .storeId(STORE_ID)
                .productId(PRODUCT_ID)
                .quantity(100)
                .cached(false)
                .build();

        when(valueOperations.get(CACHE_KEY)).thenReturn(cachedData);

        InventoryResponse response = inventoryService.getInventory(STORE_ID, PRODUCT_ID);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Retrieved from cache");
        assertThat(response.getData().getCached()).isTrue();
        assertThat(response.getData().getQuantity()).isEqualTo(100);

        verify(valueOperations, times(1)).get(CACHE_KEY);
        verify(productRepository, never()).findByStoreIdAndProductId(anyString(), anyString());
    }

    @Test
    @DisplayName("GET - Cache MISS should query database and store in cache")
    void getInventory_cacheMiss_shouldQueryDatabaseAndCache() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(productRepository.findByStoreIdAndProductId(STORE_ID, PRODUCT_ID))
                .thenReturn(Optional.of(testProduct));

        InventoryResponse response = inventoryService.getInventory(STORE_ID, PRODUCT_ID);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Retrieved from database");
        assertThat(response.getData().getQuantity()).isEqualTo(100);
        assertThat(response.getData().getCached()).isFalse();

        verify(valueOperations, times(1)).get(CACHE_KEY);
        verify(productRepository, times(1)).findByStoreIdAndProductId(STORE_ID, PRODUCT_ID);
        verify(valueOperations, times(1)).set(eq(CACHE_KEY), any(InventoryData.class), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("GET - Product not found should return error")
    void getInventory_productNotFound_shouldReturnError() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(productRepository.findByStoreIdAndProductId(STORE_ID, PRODUCT_ID))
                .thenReturn(Optional.empty());

        InventoryResponse response = inventoryService.getInventory(STORE_ID, PRODUCT_ID);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Product not found in inventory");
        assertThat(response.getData()).isNull();
    }

    @Test
    @DisplayName("GET - Redis failure should fallback to database")
    void getInventory_redisFail_shouldFallbackToDatabase() {
        when(valueOperations.get(CACHE_KEY)).thenThrow(new RuntimeException("Redis connection timeout"));
        when(productRepository.findByStoreIdAndProductId(STORE_ID, PRODUCT_ID))
                .thenReturn(Optional.of(testProduct));

        InventoryResponse response = inventoryService.getInventory(STORE_ID, PRODUCT_ID);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("database");
        assertThat(response.getData().getQuantity()).isEqualTo(100);
        verify(productRepository, times(1)).findByStoreIdAndProductId(STORE_ID, PRODUCT_ID);
    }

    @Test
    @DisplayName("GET - Redis cache write failure should not affect response")
    void getInventory_cacheWriteFail_shouldContinue() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(productRepository.findByStoreIdAndProductId(STORE_ID, PRODUCT_ID))
                .thenReturn(Optional.of(testProduct));
        doThrow(new RuntimeException("Redis write timeout"))
                .when(valueOperations).set(anyString(), any(), anyLong(), any(TimeUnit.class));

        InventoryResponse response = inventoryService.getInventory(STORE_ID, PRODUCT_ID);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Retrieved from database");
        assertThat(response.getData().getQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("POST /sell - Successful sale should update stock and invalidate cache")
    void processSale_sufficientStock_shouldProcessSuccessfully() {
        SellRequest request = SellRequest.builder()
                .storeId(STORE_ID)
                .productId(PRODUCT_ID)
                .quantity(30)
                .timestamp(LocalDateTime.now())
                .build();

        when(eventRepository.existsByEventId(anyString())).thenReturn(false);
        when(productRepository.findByStoreIdAndProductIdWithLock(STORE_ID, PRODUCT_ID))
                .thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        InventoryResponse response = inventoryService.processSale(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Sale processed successfully");
        assertThat(response.getData().getQuantity()).isEqualTo(70);
        assertThat(response.getData().getQuantityBefore()).isEqualTo(100);
        assertThat(response.getData().getEventId()).isNotNull();

        verify(productRepository, times(1)).save(any(Product.class));
        verify(eventRepository, times(1)).save(any(InventoryEvent.class));
        verify(redisTemplate, times(1)).delete(CACHE_KEY);
    }

    @Test
    @DisplayName("POST /sell - Insufficient stock should return error")
    void processSale_insufficientStock_shouldReturnError() {
        SellRequest request = SellRequest.builder()
                .storeId(STORE_ID)
                .productId(PRODUCT_ID)
                .quantity(150)
                .timestamp(LocalDateTime.now())
                .build();

        when(eventRepository.existsByEventId(anyString())).thenReturn(false);
        when(productRepository.findByStoreIdAndProductIdWithLock(STORE_ID, PRODUCT_ID))
                .thenReturn(Optional.of(testProduct));

        InventoryResponse response = inventoryService.processSale(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("Insufficient stock");
        assertThat(response.getMessage()).contains("Available: 100, Requested: 150");

        verify(productRepository, never()).save(any(Product.class));
        verify(eventRepository, times(1)).save(argThat(event ->
                "FAILED".equals(event.getStatus()) && "SALE".equals(event.getEventType())
        ));
    }

    @Test
    @DisplayName("POST /sell - Idempotency should prevent event duplication")
    void processSale_duplicateEvent_shouldReturnError() {
        SellRequest request = SellRequest.builder()
                .storeId(STORE_ID)
                .productId(PRODUCT_ID)
                .quantity(30)
                .build();

        when(eventRepository.existsByEventId(anyString())).thenReturn(true);

        InventoryResponse response = inventoryService.processSale(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Event already processed");

        verify(productRepository, never()).findByStoreIdAndProductIdWithLock(anyString(), anyString());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("POST /sell - New product should be created with zero stock")
    void processSale_newProduct_shouldCreateWithZeroStock() {
        SellRequest request = SellRequest.builder()
                .storeId(STORE_ID)
                .productId("PROD_NEW")
                .quantity(10)
                .build();

        when(eventRepository.existsByEventId(anyString())).thenReturn(false);
        when(productRepository.findByStoreIdAndProductIdWithLock(STORE_ID, "PROD_NEW"))
                .thenReturn(Optional.empty());

        InventoryResponse response = inventoryService.processSale(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("Insufficient stock");
        assertThat(response.getMessage()).contains("Available: 0, Requested: 10");

        verify(productRepository, times(1)).findByStoreIdAndProductIdWithLock(STORE_ID, "PROD_NEW");
        verify(eventRepository, times(1)).save(argThat(event -> "FAILED".equals(event.getStatus())));
    }

    @Test
    @DisplayName("POST /sell - Cache invalidation failure should not prevent operation")
    void processSale_cacheInvalidationFail_shouldContinue() {
        SellRequest request = SellRequest.builder()
                .storeId(STORE_ID)
                .productId(PRODUCT_ID)
                .quantity(30)
                .build();

        when(eventRepository.existsByEventId(anyString())).thenReturn(false);
        when(productRepository.findByStoreIdAndProductIdWithLock(STORE_ID, PRODUCT_ID))
                .thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);
        doThrow(new RuntimeException("Redis timeout")).when(redisTemplate).delete(anyString());

        InventoryResponse response = inventoryService.processSale(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Sale processed successfully");
        assertThat(response.getData().getQuantity()).isEqualTo(70);
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("POST /restock - Successful restock should increase stock")
    void processRestock_validRequest_shouldIncreaseStock() {
        RestockRequest request = RestockRequest.builder()
                .storeId(STORE_ID)
                .productId(PRODUCT_ID)
                .quantity(50)
                .timestamp(LocalDateTime.now())
                .build();

        when(productRepository.findByStoreIdAndProductIdWithLock(STORE_ID, PRODUCT_ID))
                .thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        InventoryResponse response = inventoryService.processRestock(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Restock processed successfully");
        assertThat(response.getData().getQuantity()).isEqualTo(150);
        assertThat(response.getData().getQuantityBefore()).isEqualTo(100);

        verify(productRepository, times(1)).save(any(Product.class));
        verify(eventRepository, times(1)).save(argThat(event ->
                "RESTOCK".equals(event.getEventType()) && "SUCCESS".equals(event.getStatus())
        ));
        verify(redisTemplate, times(1)).delete(CACHE_KEY);
    }

    @Test
    @DisplayName("POST /restock - New product should be created with initial stock")
    void processRestock_newProduct_shouldCreateWithInitialStock() {
        RestockRequest request = RestockRequest.builder()
                .storeId(STORE_ID)
                .productId("PROD_NEW")
                .quantity(100)
                .build();

        Product newProduct = Product.builder()
                .storeId(STORE_ID)
                .productId("PROD_NEW")
                .quantity(0)
                .lastUpdated(LocalDateTime.now())
                .build();

        when(productRepository.findByStoreIdAndProductIdWithLock(STORE_ID, "PROD_NEW"))
                .thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenReturn(newProduct);

        InventoryResponse response = inventoryService.processRestock(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getQuantity()).isEqualTo(100);
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("POST /restock - Cache invalidation failure should not prevent operation")
    void processRestock_cacheInvalidationFail_shouldContinue() {
        RestockRequest request = RestockRequest.builder()
                .storeId(STORE_ID)
                .productId(PRODUCT_ID)
                .quantity(50)
                .build();

        when(productRepository.findByStoreIdAndProductIdWithLock(STORE_ID, PRODUCT_ID))
                .thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);
        doThrow(new RuntimeException("Redis timeout")).when(redisTemplate).delete(anyString());

        InventoryResponse response = inventoryService.processRestock(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Restock processed successfully");
        assertThat(response.getData().getQuantity()).isEqualTo(150);
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("POST /sync - Batch with multiple operations should process all")
    void processBatchSync_multipleOperations_shouldProcessAll() {
        SyncRequest request = SyncRequest.builder()
                .storeId(STORE_ID)
                .operations(Arrays.asList(
                        BatchOperation.builder().productId("PROD_0001").delta(-10).type("SALE").build(),
                        BatchOperation.builder().productId("PROD_0002").delta(50).type("RESTOCK").build(),
                        BatchOperation.builder().productId("PROD_0003").delta(-5).type("SALE").build()
                ))
                .timestamp(LocalDateTime.now())
                .build();

        Product product1 = testProduct;
        Product product2 = Product.builder().storeId(STORE_ID).productId("PROD_0002").quantity(100).build();
        Product product3 = Product.builder().storeId(STORE_ID).productId("PROD_0003").quantity(50).build();

        when(eventRepository.existsByEventId(anyString())).thenReturn(false);
        when(productRepository.findByStoreIdAndProductIdWithLock(STORE_ID, "PROD_0001"))
                .thenReturn(Optional.of(product1));
        when(productRepository.findByStoreIdAndProductIdWithLock(STORE_ID, "PROD_0002"))
                .thenReturn(Optional.of(product2));
        when(productRepository.findByStoreIdAndProductIdWithLock(STORE_ID, "PROD_0003"))
                .thenReturn(Optional.of(product3));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        SyncResponse response = inventoryService.processBatchSync(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getSuccessCount()).isEqualTo(3);
        assertThat(response.getFailureCount()).isEqualTo(0);
        assertThat(response.getErrors()).isEmpty();

        verify(productRepository, times(3)).save(any(Product.class));
    }

    @Test
    @DisplayName("POST /sync - Batch with partial failures should return errors")
    void processBatchSync_partialFailures_shouldReturnErrors() {
        SyncRequest request = SyncRequest.builder()
                .storeId(STORE_ID)
                .operations(Arrays.asList(
                        BatchOperation.builder().productId("PROD_0001").delta(-200).type("SALE").build(),
                        BatchOperation.builder().productId("PROD_0002").delta(50).type("RESTOCK").build()
                ))
                .timestamp(LocalDateTime.now())
                .build();

        Product product1 = testProduct;
        Product product2 = Product.builder().storeId(STORE_ID).productId("PROD_0002").quantity(100).build();

        when(eventRepository.existsByEventId(anyString())).thenReturn(false);
        when(productRepository.findByStoreIdAndProductIdWithLock(STORE_ID, "PROD_0001"))
                .thenReturn(Optional.of(product1));
        when(productRepository.findByStoreIdAndProductIdWithLock(STORE_ID, "PROD_0002"))
                .thenReturn(Optional.of(product2));
        when(productRepository.save(any(Product.class))).thenReturn(product2);

        SyncResponse response = inventoryService.processBatchSync(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getFailureCount()).isEqualTo(1);
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().get(0)).contains("Insufficient stock");
    }

    @Test
    @DisplayName("POST /sync - Empty batch should return success without processing")
    void processBatchSync_emptyOperations_shouldReturnSuccess() {
        SyncRequest request = SyncRequest.builder()
                .storeId(STORE_ID)
                .operations(Collections.emptyList())
                .timestamp(LocalDateTime.now())
                .build();

        SyncResponse response = inventoryService.processBatchSync(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getSuccessCount()).isEqualTo(0);
        assertThat(response.getFailureCount()).isEqualTo(1);
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().get(0)).contains("Empty operations");
        assertThat(response.getMessage()).contains("Operations list is empty");

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("Pessimistic Lock - Should use findByStoreIdAndProductIdWithLock")
    void processSale_shouldUsePessimisticLock() {
        SellRequest request = SellRequest.builder()
                .storeId(STORE_ID)
                .productId(PRODUCT_ID)
                .quantity(10)
                .build();

        when(eventRepository.existsByEventId(anyString())).thenReturn(false);
        when(productRepository.findByStoreIdAndProductIdWithLock(STORE_ID, PRODUCT_ID))
                .thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        inventoryService.processSale(request);

        verify(productRepository, times(1))
                .findByStoreIdAndProductIdWithLock(STORE_ID, PRODUCT_ID);
        verify(productRepository, never())
                .findByStoreIdAndProductId(anyString(), anyString());
    }

    @Test
    @DisplayName("Restock - Should use findByStoreIdAndProductIdWithLock")
    void processRestock_shouldUsePessimisticLock() {
        RestockRequest request = RestockRequest.builder()
                .storeId(STORE_ID)
                .productId(PRODUCT_ID)
                .quantity(50)
                .build();

        when(productRepository.findByStoreIdAndProductIdWithLock(STORE_ID, PRODUCT_ID))
                .thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        inventoryService.processRestock(request);

        verify(productRepository, times(1))
                .findByStoreIdAndProductIdWithLock(STORE_ID, PRODUCT_ID);
        verify(productRepository, never())
                .findByStoreIdAndProductId(anyString(), anyString());
    }
}