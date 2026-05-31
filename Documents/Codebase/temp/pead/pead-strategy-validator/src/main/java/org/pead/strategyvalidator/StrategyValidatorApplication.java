package org.pead.strategyvalidator;

import org.pead.common.config.StrategyConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"org.pead.strategyvalidator", "org.pead.common"})
@EnableConfigurationProperties(StrategyConfig.class)
@EnableScheduling
@EnableKafkaRetryTopic
public class StrategyValidatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(StrategyValidatorApplication.class, args);
    }
}
