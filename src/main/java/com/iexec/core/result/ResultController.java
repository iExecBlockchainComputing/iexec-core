package com.iexec.core.result;

import com.iexec.common.result.ResultModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.ResponseEntity.ok;

@Slf4j
@RestController
public class ResultController {// implements TaskInterface {

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
    public ResponseEntity<List> getResult(@PathVariable("taskId") String taskId) {
        List<Result> results = resultService.getResultByTaskId(taskId);
        return ok(results);
    }


}

