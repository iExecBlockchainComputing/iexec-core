package com.iexec.core.result.repo.proxy;

import com.iexec.core.result.repo.proxy.Result;

import java.util.Optional;

public abstract class ResultRepo {

    private static final String RESULT_FILENAME_PREFIX = "iexec-result-";

    public static String getResultFilename(String hash) {
        return RESULT_FILENAME_PREFIX + hash;
    }

    protected abstract String addResult(Result result, byte[] data);

    protected abstract Optional<byte[]> getResult(String chainTaskId);

    public boolean doesResultExist(String chainTaskId) {
        return getResult(chainTaskId).isPresent();
    }
}
