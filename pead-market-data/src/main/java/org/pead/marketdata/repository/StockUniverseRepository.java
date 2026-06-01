package org.pead.marketdata.repository;

import org.pead.marketdata.domain.StockUniverse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockUniverseRepository extends JpaRepository<StockUniverse, UUID> {

    Optional<StockUniverse> findByName(String name);
}
