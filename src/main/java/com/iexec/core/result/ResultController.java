package com.iexec.core.result;

import com.iexec.common.result.ResultModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.ResponseEntity.ok;

@Slf4j
@RestController
public class ResultController {

    private ResultService resultService;

    public ResultController(ResultService resultService) {
        this.resultService = resultService;
    }

    @PostMapping("/results")
    public ResponseEntity addResult(@RequestBody ResultModel model) {
        Result result = resultService.addResult(
                Result.builder()
                        .taskId(model.getTaskId())
                        .image(model.getImage())
                        .cmd(model.getCmd())
                        .stdout(model.getStdout())
                        .payload(model.getPayload()).build());

        return ok(result.getTaskId());
    }

    @GetMapping("/tasks/{taskId}/results")
    public ResponseEntity getResult(@PathVariable("taskId") String taskId) {
        return ok(resultService.getResultByTaskId(taskId));
    }


}

