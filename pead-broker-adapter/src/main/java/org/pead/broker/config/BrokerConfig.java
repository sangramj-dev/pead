package org.pead.broker.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "pead.broker")
@Getter
@Setter
public class BrokerConfig {

    private String type = "paper";
    private BigDecimal slippagePct = new BigDecimal("0.001");
    private BigDecimal initialCapital = new BigDecimal("1000000");
}
