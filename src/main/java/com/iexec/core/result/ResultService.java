package com.iexec.core.result;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ResultService {

    private ResultRepository resultRepository;

    public ResultService(ResultRepository resultRepository) {
        this.resultRepository = resultRepository;
    }

    public Result addResult(Result result) {
        log.info("Adding new resultData [taskId:{}, image:{}, cmd:{}, stdout:{}, payload:{}]",
                result.getTaskId(), result.getImage(), result.getCmd(), result.getStdout(), result.getPayload());
        return resultRepository.save(result);
    }

    public Optional<Result> getResult(String id) {
        return resultRepository.findById(id);
    }


    public List<Result> getResultByTaskId(String taskId) {
        return resultRepository.findByTaskId(taskId);
    }


}
