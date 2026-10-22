package com.qaryati;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseMigrationIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationsValidateAndSourceRelationshipsKeepRealForeignKeys() {
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(2, jdbc.queryForObject(
                "select count(*) from public.flyway_schema_history where version in ('1', '2') and success", Integer.class));
        assertEquals(27, jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema='public' and table_type='BASE TABLE' and table_name <> 'flyway_schema_history'
                """, Integer.class));
        var sourceReferences = jdbc.queryForList("""
                select c.relname from pg_constraint k
                join pg_class c on c.oid=k.conrelid
                where k.contype='f' and k.convalidated and k.confrelid='public.sources'::regclass
                """, String.class);
        assertEquals(Set.of("population_records", "village_sources", "history_sources", "event_sources",
                "heritage_site_sources", "cultural_heritage_sources"), Set.copyOf(sourceReferences));
    }
}
