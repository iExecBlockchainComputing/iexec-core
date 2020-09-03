/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.dataset;

import com.iexec.common.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;

import java.util.Optional;

@Service
@Slf4j
public class DataSetService {

    private DataSetRepository dataSetRepository;


    public DataSetService(DataSetRepository dataSetRepository) {
        this.dataSetRepository = dataSetRepository;
    }

    String saveDataSet(byte[] zip) {
        if (zip == null) {
            return "";
        }

        DataSet dataSet = DataSet.builder()
                .hash(BytesUtils.bytesToString(Hash.sha3(zip)))
                .zip(zip)
                .build();

        if (dataSetRepository.findByHash(dataSet.getHash()).isPresent()) {
            log.info("DataSet already in database [hash:{}]", dataSet.getHash());
            return dataSet.getHash();
        }

        DataSet saved = dataSetRepository.save(dataSet);

        if (saved != null) {
            log.info("DataSet saved in database [hash:{}]", dataSet.getHash());
            return saved.getHash();
        }

        return "";
    }

    Optional<DataSet> getDataSetByHash(String hash) {
        return dataSetRepository.findByHash(hash);
    }

}
