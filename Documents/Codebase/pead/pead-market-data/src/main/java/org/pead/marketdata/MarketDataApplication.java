package org.pead.marketdata;

import org.pead.common.config.StrategyConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"org.pead.marketdata", "org.pead.common"})
@EnableConfigurationProperties(StrategyConfig.class)
@EnableScheduling
public class MarketDataApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketDataApplication.class, args);
    }
}
