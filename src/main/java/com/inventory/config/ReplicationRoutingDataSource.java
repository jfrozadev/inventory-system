package com.inventory.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
public class ReplicationRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        boolean isInTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        
        if (isInTransaction && !isReadOnly) {
            log.debug("Routing to PRIMARY (write)");
            return "primary";
        } else {
            log.debug("Routing to REPLICA (read)");
            return "replica";
        }
    }
}
