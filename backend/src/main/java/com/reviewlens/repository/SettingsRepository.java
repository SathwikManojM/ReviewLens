package com.reviewlens.repository;

import com.reviewlens.model.SystemSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SettingsRepository extends MongoRepository<SystemSettings, String> {
}
