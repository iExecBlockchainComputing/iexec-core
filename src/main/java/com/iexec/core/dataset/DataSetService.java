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
