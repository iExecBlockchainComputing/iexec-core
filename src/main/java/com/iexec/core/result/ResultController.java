package com.iexec.core.result;

import com.iexec.common.core.TaskInterface;
import com.iexec.common.replicate.ReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.result.ResultModel;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

@Slf4j
@RestController
public class ResultController {// implements TaskInterface {

    private ResultService resultService;

    public ResultController(ResultService resultService) {
        this.resultService = resultService;
    }

    @PostMapping("/results")
    public ResponseEntity addResult(@RequestBody ResultModel resultModel) {
        Result result = resultService.addResult(resultModel.getTaskId(), resultModel.getImage(), resultModel.getCmd(), resultModel.getStdout(), null);
        return ok(result.getTaskId());
    }

    @GetMapping("/tasks/{taskId}/results")
    public ResponseEntity<List> getResult(@PathVariable("taskId") String taskId) {
        List<Result> results = resultService.getResultByTaskId(taskId);
        return ok(results);
    }



}

