-- ReviewLens MySQL schema (reference).
-- Spring Boot creates/updates these tables automatically (spring.jpa.hibernate.ddl-auto=update);
-- this script documents the design and can be used to create the schema manually.

CREATE DATABASE IF NOT EXISTS reviewlens CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE reviewlens;

CREATE TABLE IF NOT EXISTS users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(100) NOT NULL,
  email         VARCHAR(150) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,          -- BCrypt
  created_at    DATETIME(6)  NOT NULL
);

CREATE TABLE IF NOT EXISTS datasets (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  owner_id          BIGINT       NOT NULL,
  name              VARCHAR(150) NOT NULL,
  file_name         VARCHAR(255) NOT NULL,
  stored_path       VARCHAR(500) NOT NULL,
  status            VARCHAR(20)  NOT NULL,      -- UPLOADED, PARSING, ANALYZING, COMPLETED, FAILED
  total_reviews     INT,
  processed_reviews INT,
  skipped_rows      INT,
  product_count     INT,
  column_mapping    VARCHAR(1000),              -- detected CSV columns, e.g. text=reviews.text
  error_message     VARCHAR(1000),
  created_at        DATETIME(6)  NOT NULL,
  completed_at      DATETIME(6),
  CONSTRAINT fk_dataset_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS products (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  dataset_id  BIGINT       NOT NULL,
  external_id VARCHAR(255),                     -- ASIN from the source dataset
  name        VARCHAR(500) NOT NULL,
  brand       VARCHAR(150),
  category    VARCHAR(500),
  INDEX idx_product_dataset (dataset_id),
  CONSTRAINT fk_product_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id)
);

CREATE TABLE IF NOT EXISTS reviews (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  dataset_id        BIGINT NOT NULL,
  product_id        BIGINT NOT NULL,
  reviewer          VARCHAR(150),
  rating            INT,
  title             VARCHAR(500),
  review_text       MEDIUMTEXT NOT NULL,        -- original text
  clean_text        MEDIUMTEXT,                 -- pre-processed text (Module 3)
  review_date       DATE,
  overall_sentiment VARCHAR(10),                -- POSITIVE / NEGATIVE / NEUTRAL
  overall_score     DOUBLE,                     -- -1 .. +1
  INDEX idx_review_dataset (dataset_id),
  INDEX idx_review_product (product_id),
  CONSTRAINT fk_review_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id),
  CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES products(id)
);

-- Core ABSA output: one row per (review, aspect)
CREATE TABLE IF NOT EXISTS aspect_sentiments (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  review_id  BIGINT      NOT NULL,
  product_id BIGINT      NOT NULL,
  dataset_id BIGINT      NOT NULL,
  aspect     VARCHAR(50) NOT NULL,              -- Camera, Battery, Display, Price ...
  term       VARCHAR(100),                      -- word that triggered the aspect ("battery life")
  sentiment  VARCHAR(10) NOT NULL,
  score      DOUBLE      NOT NULL,
  snippet    VARCHAR(500),                      -- opinion clause
  INDEX idx_aspect_dataset (dataset_id, aspect),
  INDEX idx_aspect_product (product_id, aspect),
  INDEX idx_aspect_review (review_id),
  CONSTRAINT fk_aspect_review  FOREIGN KEY (review_id)  REFERENCES reviews(id),
  CONSTRAINT fk_aspect_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT fk_aspect_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id)
);

-- Keyword analysis cache (product_id NULL = whole dataset)
CREATE TABLE IF NOT EXISTS keywords (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  dataset_id BIGINT       NOT NULL,
  product_id BIGINT,
  term       VARCHAR(100) NOT NULL,
  frequency  INT          NOT NULL,
  score      DOUBLE       NOT NULL,
  type       VARCHAR(10)  NOT NULL,             -- KEYWORD, PRAISE, CONCERN
  INDEX idx_keyword_scope (dataset_id, product_id),
  CONSTRAINT fk_keyword_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id),
  CONSTRAINT fk_keyword_product FOREIGN KEY (product_id) REFERENCES products(id)
);
