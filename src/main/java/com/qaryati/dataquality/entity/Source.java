
package com.qaryati.dataquality.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "sources", schema = "public")
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 350)
    private String title;

    @Column(name = "publisher", length = 200)
    private String publisher;

    @Column(name = "author", length = 200)
    private String author;

    @Column(name = "source_type", nullable = false, length = 28)
    private String sourceType;

    @Column(name = "url", columnDefinition = "text")
    private String url;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    @Column(name = "accessed_at")
    private LocalDate accessedAt;

    @Column(name = "citation", columnDefinition = "text")
    private String citation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Source() {
    }

    public Source(String title, String sourceType) {
        this.title = title;
        this.sourceType = sourceType;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public LocalDate getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(LocalDate publicationDate) {
        this.publicationDate = publicationDate;
    }

    public LocalDate getAccessedAt() {
        return accessedAt;
    }

    public void setAccessedAt(LocalDate accessedAt) {
        this.accessedAt = accessedAt;
    }

    public String getCitation() {
        return citation;
    }

    public void setCitation(String citation) {
        this.citation = citation;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
