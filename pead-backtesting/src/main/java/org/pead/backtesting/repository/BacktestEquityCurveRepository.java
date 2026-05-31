package org.pead.backtesting.repository;

import org.pead.backtesting.domain.BacktestEquityCurveEntity;
import org.pead.backtesting.domain.BacktestEquityCurveId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BacktestEquityCurveRepository extends JpaRepository<BacktestEquityCurveEntity, BacktestEquityCurveId> {

    List<BacktestEquityCurveEntity> findByBacktestIdOrderByCurveDate(UUID backtestId);
}
