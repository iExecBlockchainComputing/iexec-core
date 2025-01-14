/*
 * Copyright 2021-2025 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.core.configuration;

import com.mongodb.client.MongoCollection;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigInteger;

@Slf4j
@ChangeUnit(order = "001", id = "moveFromReplayField", author = "iexec")
public class ConfigurationRepositoryMigration {

    public static final String CONFIGURATION_COLLECTION_NAME = "configuration";
    public static final String LEGACY_FROM_REPLAY_FIELD_NAME = "fromReplay";

    @Execution
    public boolean moveFromReplayField(MongoTemplate mongockTemplate, ReplayConfigurationRepository replayConfigurationRepository) {
        if (replayConfigurationRepository.count() > 0) {
            log.info("Migration of fromReplay field is useless (already up-to-date)");
            return false;
        }
        // move field from configuration to replayConfiguration
        MongoCollection<Document> configurationCollection = mongockTemplate.getDb()
                .getCollection(CONFIGURATION_COLLECTION_NAME);
        Document configuration = configurationCollection.find().first();
        if (configuration == null) {
            log.info("Migration of fromReplay field is useless (no legacy)");
            return false;
        }
        Object legacyFromReplayObject = configuration.get(LEGACY_FROM_REPLAY_FIELD_NAME);
        if (legacyFromReplayObject == null) {
            log.info("Migration of fromReplay field is useless (missing field from legacy)");
            return false;
        }
        BigInteger legacyFromReplay = new BigInteger((String) legacyFromReplayObject);
        ReplayConfiguration replayConfiguration = new ReplayConfiguration();
        replayConfiguration.setFromBlockNumber(legacyFromReplay);
        replayConfigurationRepository.save(replayConfiguration);

        //remove legacy field from configuration
        configurationCollection.deleteOne(configuration);
        configuration.remove(LEGACY_FROM_REPLAY_FIELD_NAME);
        configurationCollection.insertOne(configuration);
        return true;
    }

    @RollbackExecution
    public void rollback(ReplayConfigurationRepository replayConfigurationRepository) {
        replayConfigurationRepository.deleteAll();
    }

}
