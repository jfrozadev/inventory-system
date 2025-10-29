package com.inventory.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "store_id", nullable = false, length = 50)
    private String storeId;

    @Column(name = "product_id", nullable = false, length = 50)
    private String productId;

    @Column(name = "quantity_before")
    private Integer quantityBefore;

    @Column(name = "quantity_after")
    private Integer quantityAfter;

    @Column(name = "quantity_delta", nullable = false)
    private Integer quantityDelta;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public enum EventType {
        SALE,
        RESTOCK,
        ADJUSTMENT,
        SYNC
    }

    public enum EventStatus {
        PENDING,
        PROCESSING,
        SUCCESS,
        FAILED,
        COMPENSATED
    }
}