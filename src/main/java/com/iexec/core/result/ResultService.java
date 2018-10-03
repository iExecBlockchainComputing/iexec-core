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

    public Result addResult(String taskId, String image, String cmd, String stdout, byte[] payload) {
        log.info("Adding new resultData [taskId:{}, image:{}, cmd:{}, stdout:{}, payload:{}]",
                taskId, image, cmd, stdout, payload);
        return resultRepository.save(Result.builder().
                taskId(taskId)
                .image(image)
                .cmd(cmd)
                .stdout(stdout)
                .payload(payload)
                .build());
    }

    public Optional<Result> getResult(String id) {
        return resultRepository.findById(id);
    }


    public List<Result> getResultByTaskId(String taskId) {
        return resultRepository.findByTaskId(taskId);
    }


}
