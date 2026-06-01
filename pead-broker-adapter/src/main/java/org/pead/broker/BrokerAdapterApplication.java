package org.pead.broker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"org.pead.broker", "org.pead.common"})
public class BrokerAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(BrokerAdapterApplication.class, args);
    }
}
