CREATE TABLE search_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    search_query VARCHAR(500) NOT NULL,
    api          VARCHAR(50)  NOT NULL,
    result_count INT          NOT NULL,
    searched_at  DATETIME(6)  NOT NULL
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
