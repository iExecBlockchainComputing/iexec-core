package com.iexec.core.dataset;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

import static org.springframework.http.ResponseEntity.ok;

@Slf4j
@RestController
public class DataSetController {

    private DataSetService dataSetService;

    public DataSetController(DataSetService dataSetService) {

        this.dataSetService = dataSetService;
    }

    @PostMapping("/datasets")
    public ResponseEntity<String> pushDataSet(@RequestParam("file") MultipartFile file) throws IOException {
        String hash = dataSetService.saveDataSet(file.getBytes());

        if (hash.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).build();
        }

        return ok(hash);
    }

    @CrossOrigin
    @GetMapping(value = "/datasets/{hash}", produces = "application/zip")
    public ResponseEntity<byte[]> getDataSet(@PathVariable("hash") String hash) {
        Optional<DataSet> dataSet = dataSetService.getDataSetByHash(hash);

        if (dataSet.isPresent()) {
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=" + dataSet.get().getHash())
                    .body(dataSet.get().getZip());
        }

        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

}

