package com.kov.movieinfo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kov.movieinfo.entity.SearchLog;

public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {}
