package com.ian.community.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigConditionalTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SecurityConfig.class);

    @Test
    void nonWebWorkerContextDoesNotLoadServletSecurityConfiguration() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(SecurityConfig.class)
        );
    }
}
