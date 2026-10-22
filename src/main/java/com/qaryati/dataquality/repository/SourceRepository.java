
package com.qaryati.dataquality.repository;

import com.qaryati.dataquality.entity.Source;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceRepository extends JpaRepository<Source, Long> {

    List<Source> findBySourceType(String sourceType);

    List<Source> findByTitleContainingIgnoreCase(String title);

    List<Source> findByPublisherIgnoreCase(String publisher);
}
