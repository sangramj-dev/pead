package org.pead.strategyvalidator.repository;

import org.pead.strategyvalidator.domain.ScoringDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScoringDetailRepository extends JpaRepository<ScoringDetail, UUID> {

    List<ScoringDetail> findBySignalId(UUID signalId);
}
