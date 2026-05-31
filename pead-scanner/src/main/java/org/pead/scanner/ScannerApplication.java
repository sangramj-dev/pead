package org.pead.scanner;

import org.pead.common.config.StrategyConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafkaStreams;

@SpringBootApplication(scanBasePackages = {"org.pead.scanner", "org.pead.common"})
@EnableConfigurationProperties(StrategyConfig.class)
@EnableKafkaStreams
public class ScannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScannerApplication.class, args);
    }
}
