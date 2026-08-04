package com.ian.community.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigurationTest {

    @Test
    @DisplayName("운영 Profile은 Console과 SQL Log를 끄고 보안 Cookie를 사용한다")
    void productionSecurityDefaults() throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load(
                        "application-prod",
                        new ClassPathResource("application-prod.yaml")
                );

        assertThat(sources).hasSize(1);
        PropertySource<?> source = sources.getFirst();

        assertThat(source.getProperty("spring.h2.console.enabled"))
                .isEqualTo(false);
        assertThat(source.getProperty("spring.jpa.properties.hibernate.show_sql"))
                .isEqualTo(false);
        assertThat(source.getProperty("spring.jpa.open-in-view"))
                .isEqualTo(false);
        assertThat(source.getProperty("app.cookie.secure"))
                .isEqualTo(true);
        assertThat(source.getProperty("server.address"))
                .isEqualTo("127.0.0.1");
        assertThat(source.getProperty("app.frontend-origin"))
                .isEqualTo("${FRONTEND_ORIGIN}");
    }
}
