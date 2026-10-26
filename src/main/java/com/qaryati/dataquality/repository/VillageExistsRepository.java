
package com.qaryati.dataquality.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VillageExistsRepository {

    private final JdbcTemplate jdbcTemplate;

    public VillageExistsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsById(Long villageId) {

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.villages WHERE id = ?",
                Integer.class,
                villageId
        );

        return count != null && count > 0;
    }
}
