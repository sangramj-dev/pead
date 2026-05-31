package org.pead.backtesting.repository;

import org.pead.backtesting.domain.BacktestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BacktestRepository extends JpaRepository<BacktestEntity, UUID> {
}
