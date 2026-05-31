package org.pead.strategyvalidator.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Entity
@Table(name = "scoring_details", indexes = {
        @Index(name = "idx_sd_signal_id", columnList = "signal_id")
})
@Data
public class ScoringDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "signal_id", nullable = false)
    private UUID signalId;

    @Column(nullable = false, length = 50)
    private String component;

    @Column(name = "raw_value")
    private Double rawValue;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private int weight;

    @Column(name = "weighted_score", nullable = false)
    private int weightedScore;
}
