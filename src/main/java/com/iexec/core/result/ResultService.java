package com.iexec.core.result;

import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
public class ResultService {

    private static final String RESULT_FILENAME_PREFIX = "iexec-result-";

    private GridFsOperations gridOperations;

    public ResultService(GridFsOperations gridOperations) {
        this.gridOperations = gridOperations;
    }

    String addResult(Result result, byte[] data) {
        InputStream inputStream = new ByteArrayInputStream(data);
        String resultFileName = getResultFilename(result.getChainTaskId());
        gridOperations.store(inputStream, resultFileName, result);
        return resultFileName;
    }

    byte[] getResultByChainTaskId(String chainTaskId) throws IOException {
        String resultFileName = getResultFilename(chainTaskId);
        GridFsResource[] resources = gridOperations.getResources(resultFileName);
        if (resources.length == 0) {
            return new byte[0];
        }
        InputStream result = resources[0].getInputStream();
        return org.apache.commons.io.IOUtils.toByteArray(result);
    }

    static String getResultFilename(String chainTaskId) {
        return RESULT_FILENAME_PREFIX + chainTaskId;
    }
}
