package com.inventory.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;


public class InventoryDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SellRequest {
        @NotBlank(message = "Store ID is required")
        private String storeId;
        
        @NotBlank(message = "Product ID is required")
        private String productId;
        
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;
        
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RestockRequest {
        @NotBlank(message = "Store ID is required")
        private String storeId;
        
        @NotBlank(message = "Product ID is required")
        private String productId;
        
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;
        
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchOperation {
        @NotBlank(message = "Product ID is required")
        private String productId;
        
        @NotNull(message = "Delta is required")
        private Integer delta;
        
        @NotBlank(message = "Type is required")
        private String type;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncRequest {
        @NotBlank(message = "Store ID is required")
        private String storeId;
        
        @NotNull(message = "Operations list is required")
        private List<BatchOperation> operations;
        
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryResponse {
        private boolean success;
        private String message;
        private InventoryData data;
        private LocalDateTime timestamp;
        
        public static InventoryResponse success(String message, InventoryData data) {
            return InventoryResponse.builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
        }
        
        public static InventoryResponse error(String message) {
            return InventoryResponse.builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
        }
    }
    

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryData {
        private String storeId;
        private String productId;
        private Integer quantity;
        private LocalDateTime lastUpdated;
        private String eventId;
        private Boolean cached;
        private Integer quantityBefore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncResponse {
        private boolean success;
        private String message;
        private Integer successCount;
        private Integer failureCount;
        private List<String> errors;
        private LocalDateTime timestamp;
    }
}
