package org.pead.broker.repository;

import org.pead.broker.domain.TradeHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TradeHistoryRepository extends JpaRepository<TradeHistoryEntity, UUID> {

    List<TradeHistoryEntity> findByTicker(String ticker);

    List<TradeHistoryEntity> findAllByOrderByExitDateDesc();
}
