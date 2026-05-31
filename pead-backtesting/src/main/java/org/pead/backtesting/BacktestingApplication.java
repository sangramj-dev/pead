package org.pead.backtesting;

import org.pead.common.config.StrategyConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StrategyConfig.class)
public class BacktestingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BacktestingApplication.class, args);
    }
}
