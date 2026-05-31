package org.pead.backtesting.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link BacktestEquityCurveEntity}.
 */
public class BacktestEquityCurveId implements Serializable {

    private UUID backtestId;
    private LocalDate curveDate;

    public BacktestEquityCurveId() {}

    public BacktestEquityCurveId(UUID backtestId, LocalDate curveDate) {
        this.backtestId = backtestId;
        this.curveDate = curveDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BacktestEquityCurveId that)) return false;
        return Objects.equals(backtestId, that.backtestId) &&
                Objects.equals(curveDate, that.curveDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(backtestId, curveDate);
    }
}
