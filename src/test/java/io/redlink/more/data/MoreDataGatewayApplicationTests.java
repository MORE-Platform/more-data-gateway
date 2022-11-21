package io.redlink.more.data;

import io.redlink.more.data.configuration.DatabaseConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(
        classes = {
                DatabaseConfiguration.class
        })
class MoreDataGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
