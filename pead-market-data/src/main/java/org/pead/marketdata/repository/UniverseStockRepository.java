package org.pead.marketdata.repository;

import org.pead.marketdata.domain.UniverseStock;
import org.pead.marketdata.domain.UniverseStockId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UniverseStockRepository extends JpaRepository<UniverseStock, UniverseStockId> {

    List<UniverseStock> findByUniverseId(UUID universeId);

    @Query("SELECT us.ticker FROM UniverseStock us WHERE us.universeId = :universeId")
    List<String> findTickersByUniverseId(@Param("universeId") UUID universeId);

    void deleteByUniverseIdAndTicker(UUID universeId, String ticker);
}
