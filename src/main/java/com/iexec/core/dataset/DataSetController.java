/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.core.dataset;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

import static org.springframework.http.ResponseEntity.ok;

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

    @GetMapping(value = "/datasets/{hash}", produces = "application/zip")
    public ResponseEntity<byte[]> getDataSet(@PathVariable("hash") String hash) {
        Optional<DataSet> dataSet = dataSetService.getDataSetByHash(hash);

        if (dataSet.isPresent()) {
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=" + dataSet.get().getHash())
                    .body(dataSet.get().getZip());
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND.value()).build();
    }

}

