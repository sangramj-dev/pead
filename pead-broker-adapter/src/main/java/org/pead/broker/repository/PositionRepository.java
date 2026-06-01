package org.pead.broker.repository;

import org.pead.broker.domain.PositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PositionRepository extends JpaRepository<PositionEntity, UUID> {

    List<PositionEntity> findByStatus(String status);

    List<PositionEntity> findByTicker(String ticker);
}
