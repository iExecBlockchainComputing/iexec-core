package com.iexec.core.result;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ResultService {

    private ResultRepository resultRepository;

    public ResultService(ResultRepository resultRepository) {
        this.resultRepository = resultRepository;
    }

    Result addResult(Result result) {
        Result savedResult = resultRepository.save(result);
        log.info("Result saved in repo [chainTaskId:{}]", savedResult.getChainTaskId());
        return savedResult;
    }

    List<Result> getResultByChainTaskId(String chainTaskId) {
        return resultRepository.findByChainTaskId(chainTaskId);
    }


}
