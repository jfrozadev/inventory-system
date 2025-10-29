package com.inventory.repository;

import com.inventory.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.storeId = :storeId AND p.productId = :productId")
    Optional<Product> findByStoreIdAndProductIdWithLock(
        @Param("storeId") String storeId,
        @Param("productId") String productId
    );

    Optional<Product> findByStoreIdAndProductId(String storeId, String productId);

    List<Product> findByStoreId(String storeId);

    boolean existsByStoreIdAndProductId(String storeId, String productId);
}
