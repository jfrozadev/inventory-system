package com.inventory.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_id", columnList = "productId"),
    @Index(name = "idx_store_product", columnList = "storeId,productId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, length = 50)
    private String storeId;

    @Column(name = "product_id", nullable = false, length = 50)
    private String productId;

    @Column(nullable = false)
    private Integer quantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastUpdated = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }

    public void addQuantity(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount to add cannot be negative");
        }
        this.quantity += amount;
    }

    public void removeQuantity(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount to remove cannot be negative");
        }
        if (this.quantity < amount) {
            throw new IllegalStateException(
                String.format("Insufficient stock. Available: %d, Requested: %d", 
                    this.quantity, amount)
            );
        }
        this.quantity -= amount;
    }
}
