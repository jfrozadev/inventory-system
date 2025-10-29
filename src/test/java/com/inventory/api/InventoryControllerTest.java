package com.inventory.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.inventory.api.InventoryDTOs.*;
import com.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(InventoryController.class)
@DisplayName("InventoryController Tests")
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryService inventoryService;

    private InventoryData testData;
    private InventoryResponse successResponse;
    private InventoryResponse errorResponse;

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new JavaTimeModule());

        testData = InventoryData.builder()
                .storeId("STORE_001")
                .productId("PROD_0001")
                .quantity(100)
                .lastUpdated(LocalDateTime.now())
                .cached(false)
                .build();

        successResponse = InventoryResponse.builder()
                .success(true)
                .message("Success")
                .data(testData)
                .timestamp(LocalDateTime.now())
                .build();

        errorResponse = InventoryResponse.builder()
                .success(false)
                .message("Error occurred")
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/inventory - Should return 200 when product exists")
    void getInventory_ValidRequest_ShouldReturn200() throws Exception {

        when(inventoryService.getInventory("STORE_001", "PROD_0001"))
                .thenReturn(successResponse);

        mockMvc.perform(get("/api/v1/inventory")
                        .param("storeId", "STORE_001")
                        .param("productId", "PROD_0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.storeId").value("STORE_001"))
                .andExpect(jsonPath("$.data.productId").value("PROD_0001"))
                .andExpect(jsonPath("$.data.quantity").value(100))
                .andExpect(jsonPath("$.data.cached").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/inventory - Should return 404 when product not found")
    void getInventory_ProductNotFound_ShouldReturn404() throws Exception {

        when(inventoryService.getInventory("STORE_001", "PROD_9999"))
                .thenReturn(errorResponse);

        mockMvc.perform(get("/api/v1/inventory")
                        .param("storeId", "STORE_001")
                        .param("productId", "PROD_9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Error occurred"));
    }

    @Test
    @DisplayName("GET /api/v1/inventory - Should return 400 when missing storeId")
    void getInventory_MissingStoreId_ShouldReturn400() throws Exception {

        mockMvc.perform(get("/api/v1/inventory")
                        .param("productId", "PROD_0001"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/inventory - Should return 400 when missing productId")
    void getInventory_MissingProductId_ShouldReturn400() throws Exception {

        mockMvc.perform(get("/api/v1/inventory")
                        .param("storeId", "STORE_001"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/inventory - Should return cached data")
    void getInventory_CachedData_ShouldIndicateCached() throws Exception {

        testData.setCached(true);
        successResponse.setMessage("Inventory retrieved from cache");
        when(inventoryService.getInventory("STORE_001", "PROD_0001"))
                .thenReturn(successResponse);

        mockMvc.perform(get("/api/v1/inventory")
                        .param("storeId", "STORE_001")
                        .param("productId", "PROD_0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cached").value(true))
                .andExpect(jsonPath("$.message").value("Inventory retrieved from cache"));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/sell - Should return 200 when sale succeeds")
    void processSale_ValidRequest_ShouldReturn200() throws Exception {

        SellRequest request = SellRequest.builder()
                .storeId("STORE_001")
                .productId("PROD_0001")
                .quantity(10)
                .timestamp(LocalDateTime.now())
                .build();

        testData.setQuantity(90);
        testData.setQuantityBefore(100);
        when(inventoryService.processSale(any(SellRequest.class)))
                .thenReturn(successResponse);

        mockMvc.perform(post("/api/v1/inventory/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.quantity").value(90));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/sell - Should return 400 when insufficient stock")
    void processSale_InsufficientStock_ShouldReturn400() throws Exception {

        SellRequest request = SellRequest.builder()
                .storeId("STORE_001")
                .productId("PROD_0001")
                .quantity(200)
                .timestamp(LocalDateTime.now())
                .build();

        when(inventoryService.processSale(any(SellRequest.class)))
                .thenReturn(errorResponse);

        mockMvc.perform(post("/api/v1/inventory/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/sell - Should return 400 when storeId is blank")
    void processSale_BlankStoreId_ShouldReturn400() throws Exception {

        SellRequest request = SellRequest.builder()
                .storeId("")
                .productId("PROD_0001")
                .quantity(10)
                .build();

        mockMvc.perform(post("/api/v1/inventory/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/inventory/sell - Should return 400 when quantity is null")
    void processSale_NullQuantity_ShouldReturn400() throws Exception {

        SellRequest request = SellRequest.builder()
                .storeId("STORE_001")
                .productId("PROD_0001")
                .quantity(null)
                .build();

        mockMvc.perform(post("/api/v1/inventory/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/inventory/sell - Should return 400 when quantity is zero")
    void processSale_ZeroQuantity_ShouldReturn400() throws Exception {

        SellRequest request = SellRequest.builder()
                .storeId("STORE_001")
                .productId("PROD_0001")
                .quantity(0)
                .build();

        mockMvc.perform(post("/api/v1/inventory/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/inventory/restock - Should return 200 when restock succeeds")
    void processRestock_ValidRequest_ShouldReturn200() throws Exception {

        RestockRequest request = RestockRequest.builder()
                .storeId("STORE_001")
                .productId("PROD_0001")
                .quantity(50)
                .timestamp(LocalDateTime.now())
                .build();

        testData.setQuantity(150);
        testData.setQuantityBefore(100);
        when(inventoryService.processRestock(any(RestockRequest.class)))
                .thenReturn(successResponse);

        mockMvc.perform(post("/api/v1/inventory/restock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.quantity").value(150));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/restock - Should return 400 when validation fails")
    void processRestock_InvalidRequest_ShouldReturn400() throws Exception {

        RestockRequest request = RestockRequest.builder()
                .storeId("")
                .productId("PROD_0001")
                .quantity(-10)
                .build();

        mockMvc.perform(post("/api/v1/inventory/restock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }


    @Test
    @DisplayName("POST /api/v1/inventory/sync - Should return 200 when all operations succeed")
    void processBatchSync_AllSuccess_ShouldReturn200() throws Exception {

        BatchOperation op1 = BatchOperation.builder()
                .productId("PROD_0001")
                .delta(-10)
                .type("SALE")
                .build();

        BatchOperation op2 = BatchOperation.builder()
                .productId("PROD_0002")
                .delta(20)
                .type("RESTOCK")
                .build();

        SyncRequest request = SyncRequest.builder()
                .storeId("STORE_001")
                .operations(Arrays.asList(op1, op2))
                .timestamp(LocalDateTime.now())
                .build();

        SyncResponse syncResponse = SyncResponse.builder()
                .success(true)
                .message("Batch sync completed")
                .successCount(2)
                .failureCount(0)
                .errors(Arrays.asList())
                .timestamp(LocalDateTime.now())
                .build();

        when(inventoryService.processBatchSync(any(SyncRequest.class)))
                .thenReturn(syncResponse);

        mockMvc.perform(post("/api/v1/inventory/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/sync - Should return 206 when partial failure")
    void processBatchSync_PartialFailure_ShouldReturn206() throws Exception {

        BatchOperation op1 = BatchOperation.builder()
                .productId("PROD_0001")
                .delta(-10)
                .type("SALE")
                .build();

        SyncRequest request = SyncRequest.builder()
                .storeId("STORE_001")
                .operations(Arrays.asList(op1))
                .timestamp(LocalDateTime.now())
                .build();

        SyncResponse syncResponse = SyncResponse.builder()
                .success(false)
                .message("Batch sync completed with errors")
                .successCount(0)
                .failureCount(1)
                .errors(Arrays.asList("Insufficient stock"))
                .timestamp(LocalDateTime.now())
                .build();

        when(inventoryService.processBatchSync(any(SyncRequest.class)))
                .thenReturn(syncResponse);

        mockMvc.perform(post("/api/v1/inventory/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isPartialContent())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.failureCount").value(1));
    }


    @Test
    void processBatchSync_EmptyOperations_ShouldReturnPartialContent() throws Exception {

        InventoryDTOs.SyncRequest request = InventoryDTOs.SyncRequest.builder()
                .storeId("STORE_001")
                .operations(Collections.emptyList())
                .timestamp(LocalDateTime.now())
                .build();


        InventoryDTOs.SyncResponse mockResponse = InventoryDTOs.SyncResponse.builder()
                .success(false)
                .message("Operations list is empty")
                .successCount(0)
                .failureCount(1)
                .errors(List.of("Empty operations"))
                .timestamp(LocalDateTime.now())
                .build();

        when(inventoryService.processBatchSync(any(InventoryDTOs.SyncRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/inventory/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isPartialContent());
    }

    @Test
    @DisplayName("GET /api/v1/inventory/health - Should return 200")
    void healthCheck_ShouldReturn200() throws Exception {

        mockMvc.perform(get("/api/v1/inventory/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Inventory Service is running"));
    }

    @Test
    @DisplayName("POST - Should return 415 when content type is not JSON")
    void post_InvalidContentType_ShouldReturn415() throws Exception {

        SellRequest request = SellRequest.builder()
                .storeId("STORE_001")
                .productId("PROD_0001")
                .quantity(10)
                .build();

        mockMvc.perform(post("/api/v1/inventory/sell")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("POST - Should return 400 when request body is malformed")
    void post_MalformedJson_ShouldReturn400() throws Exception {

        mockMvc.perform(post("/api/v1/inventory/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json"))
                .andExpect(status().isBadRequest());
    }
}
