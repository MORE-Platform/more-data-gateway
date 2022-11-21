package io.redlink.more.data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;

@SpringBootApplication(
        exclude = ElasticsearchRestClientAutoConfiguration.class
)
public class MoreDataGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoreDataGatewayApplication.class, args);
    }

}
