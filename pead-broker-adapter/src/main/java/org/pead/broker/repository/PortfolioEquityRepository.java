package org.pead.broker.repository;

import org.pead.broker.domain.PortfolioEquityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortfolioEquityRepository extends JpaRepository<PortfolioEquityEntity, Long> {

    List<PortfolioEquityEntity> findAllByOrderByEquityDateAsc();
}
