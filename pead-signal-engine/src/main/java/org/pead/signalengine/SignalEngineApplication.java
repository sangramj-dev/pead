package org.pead.signalengine;

import org.pead.common.config.StrategyConfig;
import org.pead.signalengine.config.SignalEngineConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({StrategyConfig.class, SignalEngineConfig.class})
public class SignalEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(SignalEngineApplication.class, args);
    }
}
