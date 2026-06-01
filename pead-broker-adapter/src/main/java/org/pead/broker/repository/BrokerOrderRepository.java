package org.pead.broker.repository;

import org.pead.broker.domain.BrokerOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BrokerOrderRepository extends JpaRepository<BrokerOrderEntity, UUID> {

    List<BrokerOrderEntity> findByStatus(String status);

    List<BrokerOrderEntity> findBySignalId(String signalId);

    List<BrokerOrderEntity> findTop50ByOrderByCreatedAtDesc();
}
