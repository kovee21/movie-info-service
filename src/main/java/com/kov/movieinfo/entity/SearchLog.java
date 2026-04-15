package com.kov.movieinfo.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "search_log")
@Access(AccessType.FIELD)
public class SearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // `query` is a reserved word in MySQL — map to `search_query`.
    @Column(name = "search_query", nullable = false, length = 500)
    private String query;

    @Column(name = "api", nullable = false, length = 50)
    private String api;

    @Column(name = "result_count", nullable = false)
    private int resultCount;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;

    protected SearchLog() {
        // required by JPA
    }

    public SearchLog(String query, String api, int resultCount, LocalDateTime searchedAt) {
        this.query = query;
        this.api = api;
        this.resultCount = resultCount;
        this.searchedAt = searchedAt;
    }

    public Long getId() {
        return id;
    }

    public String getQuery() {
        return query;
    }

    public String getApi() {
        return api;
    }

    public int getResultCount() {
        return resultCount;
    }

    public LocalDateTime getSearchedAt() {
        return searchedAt;
    }
}
