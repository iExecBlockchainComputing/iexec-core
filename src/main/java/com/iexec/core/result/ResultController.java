package com.iexec.core.result;

import com.iexec.common.result.ResultModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
                        .zip(model.getZip()).build());
        return ok(result.getTaskId());
    }

    @GetMapping(value = "/results/{taskId}", produces = "application/zip")
    public ResponseEntity<byte[]> getResult(@PathVariable("taskId") String taskId) {
        List<Result> results = resultService.getResultByTaskId(taskId);
        byte[] zip = null;
        if (results.size() > 0 && results.get(0) != null) {
            zip = results.get(0).getZip();
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=iexec-result-" + taskId)
                .body(zip);
    }


}

