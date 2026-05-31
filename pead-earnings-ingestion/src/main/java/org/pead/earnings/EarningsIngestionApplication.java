package org.pead.earnings;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.pead.common.config.StrategyConfig;

@SpringBootApplication(scanBasePackages = {"org.pead.earnings", "org.pead.common"})
@EnableConfigurationProperties(StrategyConfig.class)
@EnableScheduling
public class EarningsIngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(EarningsIngestionApplication.class, args);
    }
}
