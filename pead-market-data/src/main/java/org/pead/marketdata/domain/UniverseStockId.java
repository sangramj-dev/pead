package org.pead.marketdata.domain;

import java.io.Serializable;
import java.util.UUID;

public record UniverseStockId(UUID universeId, String ticker) implements Serializable {

    // Required no-arg constructor for JPA @IdClass reflection
    public UniverseStockId() {
        this(null, null);
    }
}
