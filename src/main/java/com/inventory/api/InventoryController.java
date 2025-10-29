package com.inventory.api;

import com.inventory.api.InventoryDTOs.*;
import com.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Inventory Management", description = "APIs for distributed inventory management")
public class InventoryController {
    
    private final InventoryService inventoryService;

    @GetMapping
    @Operation(summary = "Get inventory", description = "Retrieve current stock level for a product")
    public ResponseEntity<InventoryResponse> getInventory(
            @Parameter(description = "Store ID") @RequestParam String storeId,
            @Parameter(description = "Product ID") @RequestParam String productId) {
        
        log.info("GET /api/v1/inventory - storeId: {}, productId: {}", storeId, productId);
        InventoryResponse response = inventoryService.getInventory(storeId, productId);
        
        return response.isSuccess() 
            ? ResponseEntity.ok(response)
            : ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @PostMapping("/sell")
    @Operation(summary = "Process sale", description = "Process a product sale and update inventory")
    public ResponseEntity<InventoryResponse> processSale(
            @Valid @RequestBody SellRequest request) {
        
        log.info("POST /api/v1/inventory/sell - Request: {}", request);
        InventoryResponse response = inventoryService.processSale(request);
        
        return response.isSuccess()
            ? ResponseEntity.ok(response)
            : ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @PostMapping("/restock")
    @Operation(summary = "Process restock", description = "Add inventory for a product")
    public ResponseEntity<InventoryResponse> processRestock(
            @Valid @RequestBody RestockRequest request) {
        
        log.info("POST /api/v1/inventory/restock - Request: {}", request);
        InventoryResponse response = inventoryService.processRestock(request);
        
        return response.isSuccess()
            ? ResponseEntity.ok(response)
            : ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @PostMapping("/sync")
    @Operation(summary = "Batch sync", description = "Synchronize multiple inventory operations in batch")
    public ResponseEntity<SyncResponse> processBatchSync(
            @Valid @RequestBody SyncRequest request) {
        
        log.info("POST /api/v1/inventory/sync - Store: {}, Operations: {}", 
            request.getStoreId(), request.getOperations().size());
        
        SyncResponse response = inventoryService.processBatchSync(request);
        
        return response.isSuccess()
            ? ResponseEntity.ok(response)
            : ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(response);
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the service is running")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Inventory Service is running");
    }
}
