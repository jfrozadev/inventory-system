package com.inventory.repository;

import com.inventory.model.InventoryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InventoryEventRepository extends JpaRepository<InventoryEvent, Long> {

    List<InventoryEvent> findByProductIdOrderByTimestampDesc(String productId);

    List<InventoryEvent> findByStoreIdOrderByTimestampDesc(String storeId);

    @Query("SELECT e FROM InventoryEvent e WHERE e.timestamp BETWEEN :start AND :end ORDER BY e.timestamp DESC")
    List<InventoryEvent> findEventsBetween(
        @Param("start") LocalDateTime start, 
        @Param("end") LocalDateTime end
    );

    List<InventoryEvent> findByStatusOrderByTimestampAsc(InventoryEvent.EventStatus status);

    boolean existsByEventId(String eventId);
}
