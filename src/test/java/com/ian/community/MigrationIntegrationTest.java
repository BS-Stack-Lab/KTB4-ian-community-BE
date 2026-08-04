package com.ian.community;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        properties = {
                "spring.datasource.url="
                        + "jdbc:h2:mem:migrationtest;"
                        + "DB_CLOSE_DELAY=-1;"
                        + "DB_CLOSE_ON_EXIT=FALSE",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.flyway.enabled=true",
                "spring.flyway.locations="
                        + "classpath:db/migration/h2",
                "jwt.secret="
                        + "MDEyMzQ1Njc4OWFiY2RlZj"
                        + "AxMjM0NTY3ODlhYmNkZWY="
        }
)
class MigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway 전체 Migration은 Bookmark 제약과 Index를 생성한다")
    void migrateBookmarkSchema() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_name = 'BOOKMARKS'
                """,
                Integer.class
        );
        Integer constraintCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.table_constraints
                where constraint_name = 'UK_BOOKMARKS_USER_POST'
                """,
                Integer.class
        );
        Integer activeLegacyUserCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from users
                where email = 'email@email.com'
                  and user_deleted = false
                """,
                Integer.class
        );

        assertThat(tableCount).isOne();
        assertThat(constraintCount).isOne();
        assertThat(activeLegacyUserCount).isZero();
    }
}
