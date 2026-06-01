package org.pead.marketdata.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "universe_stocks")
@IdClass(UniverseStockId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UniverseStock {

    @Id
    @Column(name = "universe_id", columnDefinition = "uuid", nullable = false)
    private UUID universeId;

    @Id
    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Column(name = "company_name", length = 100)
    private String companyName;

    @Column(length = 50)
    private String sector;

    @Column(length = 5)
    @Builder.Default
    private String exchange = "NSE";

    @Column(name = "added_at")
    private Instant addedAt;

    @PrePersist
    protected void onAdd() {
        if (addedAt == null) {
            addedAt = Instant.now();
        }
    }
}
