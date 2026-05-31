package org.pead.strategyvalidator.repository;

import org.pead.strategyvalidator.domain.RejectedCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface RejectedCandidateRepository extends JpaRepository<RejectedCandidate, UUID> {

    List<RejectedCandidate> findByScanDate(LocalDate scanDate);

    List<RejectedCandidate> findByTicker(String ticker);
}
