/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.workflow;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.commons.poco.notification.TaskNotificationType;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JavaType;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.type.MapLikeType;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.type.TypeFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.commons.poco.notification.TaskNotificationType.*;
import static org.assertj.core.api.Assertions.assertThat;

class ReplicateWorkflowTests {

    @Rule
    public TemporaryFolder folder= new TemporaryFolder();

    private ReplicateWorkflow replicateWorkflow;

    @BeforeEach
    void setup() {
        replicateWorkflow = ReplicateWorkflow.getInstance();
    }

    @Test
    void shouldNotGetNextActionWhenStatusSinceStatusIsNull(){
        assertThat(replicateWorkflow
                .getNextActionWhenStatus(null)).isNull();
    }

    @Test
    void shouldNotGetNextActionWhenStatusSinceStatusIsUnknown(){
        assertThat(replicateWorkflow
                .getNextActionWhenStatus(ReplicateStatus.ABORTED)) //unknown
                .isNull();
    }

    @Test
    void shouldNotGetNextActionWhenStatusAndCauseSinceCauseIsNull(){
        assertThat(replicateWorkflow
                .getNextActionWhenStatusAndCause(null,
                        ReplicateStatusCause.INPUT_FILES_DOWNLOAD_FAILED)) //any
                .isNull();
    }

    @Test
    void shouldNotGetNextActionWhenStatusAndCauseSinceStatusIsUnknown(){
        assertThat(replicateWorkflow
                .getNextActionWhenStatusAndCause(ReplicateStatus.ABORTED, //unknown
                        ReplicateStatusCause.ABORTED_BY_WORKER)) //any
                .isNull();
    }

    // app

    @Test
    void shouldGetNextActionOnAppDownloadFailed(){
        assertThat(replicateWorkflow
                .getNextAction(ReplicateStatus.APP_DOWNLOAD_FAILED,
                        null))
                .isEqualTo(PLEASE_ABORT);
    }

    @Test
    void shouldGetNextActionOnAppDownloadFailedWithPostComputeFailed(){
        assertThat(replicateWorkflow
                .getNextAction(ReplicateStatus.APP_DOWNLOAD_FAILED,
                        ReplicateStatusCause.POST_COMPUTE_FAILED_UNKNOWN_ISSUE))
                .isEqualTo(PLEASE_ABORT);
    }

    @Test
    void shouldGetNextActionOnAppDownloadFailedWithAppImageDownloadFailed(){
        assertThat(replicateWorkflow
                .getNextAction(ReplicateStatus.APP_DOWNLOAD_FAILED,
                        ReplicateStatusCause.APP_IMAGE_DOWNLOAD_FAILED))
                .isEqualTo(TaskNotificationType.PLEASE_CONTRIBUTE);
    }

    // data

    @Test
    void shouldGetNextActionOnDataDownloadFailed(){
        assertThat(replicateWorkflow
                .getNextAction(ReplicateStatus.DATA_DOWNLOAD_FAILED,
                        null))
                .isEqualTo(PLEASE_ABORT);
    }

    @Test
    void shouldGetNextActionOnDataDownloadFailedWithPostComputeFailed(){
        assertThat(replicateWorkflow
                .getNextAction(ReplicateStatus.DATA_DOWNLOAD_FAILED,
                        ReplicateStatusCause.POST_COMPUTE_FAILED_UNKNOWN_ISSUE))
                .isEqualTo(PLEASE_ABORT);
    }

    @Test
    void shouldGetNextActionOnDataDownloadFailedWithDatasetDownloadFailed(){
        assertThat(replicateWorkflow
                .getNextAction(ReplicateStatus.DATA_DOWNLOAD_FAILED,
                        ReplicateStatusCause.DATASET_FILE_DOWNLOAD_FAILED))
                .isEqualTo(TaskNotificationType.PLEASE_CONTRIBUTE);
    }

    @Test
    void shouldGetNextActionOnDataDownloadFailedWithDatasetBadChecksum(){
        assertThat(replicateWorkflow
                .getNextAction(ReplicateStatus.DATA_DOWNLOAD_FAILED,
                        ReplicateStatusCause.DATASET_FILE_BAD_CHECKSUM))
                .isEqualTo(TaskNotificationType.PLEASE_CONTRIBUTE);
    }

    @Test
    void shouldGetNextActionOnDataDownloadFailedWithInputFilesDownloadFailed(){
        assertThat(replicateWorkflow
                .getNextAction(ReplicateStatus.DATA_DOWNLOAD_FAILED,
                        ReplicateStatusCause.INPUT_FILES_DOWNLOAD_FAILED))
                .isEqualTo(TaskNotificationType.PLEASE_CONTRIBUTE);
    }


    /*
     * This updates the json files when transitions
     * or actions are modified.
     */
    @Test
    void workflowToJson() throws IOException {
        try {
            folder.create();

            final File transitionsFile = folder.newFile("replicate-transitions.json");
            final File actionsFile = folder.newFile("replicate-actions.json");

            String transitionsFilePath = transitionsFile.getPath();
            String actionsFilePath = actionsFile.getPath();
            ReplicateWorkflow rw = ReplicateWorkflow.getInstance();

            rw.saveWorkflowAsJsonFile(transitionsFilePath, rw.getTransitions());
            rw.saveWorkflowAsJsonFile(actionsFilePath, rw.getActionMap());

            ObjectMapper mapper = new ObjectMapper();
            final TypeFactory typeFactory = mapper.getTypeFactory();

            // Transitions
            final JavaType replicateStatusType = typeFactory.constructType(ReplicateStatus.class);
            final JavaType replicateStatusListType = typeFactory.constructParametricType(List.class, ReplicateStatus.class);
            final MapLikeType transitionsType = typeFactory.constructMapLikeType(Map.class, replicateStatusType, replicateStatusListType);

            final Map<ReplicateStatus, List<ReplicateStatus>> actualTransitions = mapper.readValue(transitionsFile, transitionsType);
            final Map<ReplicateStatus, List<ReplicateStatus>> expectedTransitions = new HashMap<>();

            expectedTransitions.put(CREATED, List.of(STARTING, FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(STARTING, List.of(STARTED, START_FAILED, FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(STARTED, List.of(APP_DOWNLOADING, FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(APP_DOWNLOADING, List.of(APP_DOWNLOADED, APP_DOWNLOAD_FAILED, FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(APP_DOWNLOAD_FAILED, List.of(CONTRIBUTING, FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(APP_DOWNLOADED, List.of(DATA_DOWNLOADING, FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(DATA_DOWNLOADING, List.of(DATA_DOWNLOADED, DATA_DOWNLOAD_FAILED, FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(DATA_DOWNLOAD_FAILED, List.of(CONTRIBUTING, FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(DATA_DOWNLOADED, List.of(COMPUTING, FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(COMPUTING, List.of(COMPUTED, COMPUTE_FAILED, FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(COMPUTED, List.of(CONTRIBUTING, CONTRIBUTE_AND_FINALIZE_ONGOING, FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(CONTRIBUTING, List.of(CONTRIBUTED, CONTRIBUTE_FAILED, FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(CONTRIBUTED, List.of(REVEALING, FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(REVEALING, List.of(REVEALED, REVEAL_FAILED, FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(REVEALED, List.of(RESULT_UPLOAD_REQUESTED, COMPLETING, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(RESULT_UPLOAD_REQUESTED, List.of(RESULT_UPLOADING, RESULT_UPLOAD_REQUEST_FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(RESULT_UPLOAD_REQUEST_FAILED, List.of(COMPLETING, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(RESULT_UPLOADING, List.of(RESULT_UPLOADED, RESULT_UPLOAD_FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(RESULT_UPLOAD_FAILED, List.of(COMPLETING, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(RESULT_UPLOADED, List.of(COMPLETING, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(CONTRIBUTE_AND_FINALIZE_ONGOING, List.of(CONTRIBUTE_AND_FINALIZE_DONE, CONTRIBUTE_AND_FINALIZE_FAILED, FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(CONTRIBUTE_AND_FINALIZE_DONE, List.of(COMPLETING, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(CONTRIBUTE_AND_FINALIZE_FAILED, List.of(FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(COMPLETING, List.of(COMPLETED, COMPLETE_FAILED, WORKER_LOST, RECOVERING, ABORTED));
            expectedTransitions.put(START_FAILED, List.of(FAILED, WORKER_LOST, ABORTED));
            expectedTransitions.put(COMPUTE_FAILED, List.of(FAILED, WORKER_LOST, ABORTED));
            expectedTransitions.put(CONTRIBUTE_FAILED, List.of(FAILED, WORKER_LOST, ABORTED));
            expectedTransitions.put(REVEAL_FAILED, List.of(FAILED, WORKER_LOST, ABORTED));
            expectedTransitions.put(ABORTED, List.of(FAILED, WORKER_LOST, COMPLETED, FAILED));
            expectedTransitions.put(WORKER_LOST, List.of(FAILED, CREATED, STARTING, START_FAILED, STARTED, APP_DOWNLOADING, APP_DOWNLOAD_FAILED, APP_DOWNLOADED, DATA_DOWNLOADING, DATA_DOWNLOAD_FAILED, DATA_DOWNLOADED, COMPUTING, COMPUTE_FAILED, COMPUTED, CONTRIBUTING, CONTRIBUTE_FAILED, CONTRIBUTED, REVEALING, REVEAL_FAILED, REVEALED, RESULT_UPLOAD_REQUESTED, RESULT_UPLOAD_REQUEST_FAILED, RESULT_UPLOADING, RESULT_UPLOAD_FAILED, RESULT_UPLOADED, CONTRIBUTE_AND_FINALIZE_ONGOING, CONTRIBUTE_AND_FINALIZE_FAILED, CONTRIBUTE_AND_FINALIZE_DONE, COMPLETING, COMPLETE_FAILED, COMPLETED, FAILED, RECOVERING, ABORTED, RECOVERING, ABORTED, ABORTED));
            expectedTransitions.put(RECOVERING, List.of(FAILED, WORKER_LOST, RECOVERING, START_FAILED, STARTED, APP_DOWNLOADING, APP_DOWNLOAD_FAILED, APP_DOWNLOADED, DATA_DOWNLOADING, DATA_DOWNLOAD_FAILED, DATA_DOWNLOADED, COMPUTING, COMPUTE_FAILED, COMPUTED, CONTRIBUTING, CONTRIBUTE_FAILED, CONTRIBUTED, REVEALING, REVEAL_FAILED, REVEALED, RESULT_UPLOAD_REQUESTED, RESULT_UPLOAD_REQUEST_FAILED, RESULT_UPLOADING, RESULT_UPLOAD_FAILED, RESULT_UPLOADED, CONTRIBUTE_AND_FINALIZE_ONGOING, CONTRIBUTE_AND_FINALIZE_FAILED, CONTRIBUTE_AND_FINALIZE_DONE, COMPLETING, COMPLETE_FAILED, COMPLETED, FAILED, ABORTED, RECOVERING, WORKER_LOST));
            expectedTransitions.put(COMPLETE_FAILED, List.of(WORKER_LOST, ABORTED));

            assertThat(actualTransitions).isEqualTo(expectedTransitions);

            // Actions
            final JavaType actionsType = typeFactory.constructMapLikeType(Map.class, ReplicateStatus.class, TaskNotificationType.class);

            final Map<ReplicateStatus, TaskNotificationType> actualActions = mapper.readValue(actionsFile, actionsType);
            final Map<ReplicateStatus, TaskNotificationType> expectedActions = new HashMap<>();
            expectedActions.put(STARTING, PLEASE_CONTINUE);
            expectedActions.put(STARTED, PLEASE_DOWNLOAD_APP);
            expectedActions.put(START_FAILED, PLEASE_ABORT);
            expectedActions.put(APP_DOWNLOADING, PLEASE_CONTINUE);
            expectedActions.put(APP_DOWNLOADED, PLEASE_DOWNLOAD_DATA);
            expectedActions.put(APP_DOWNLOAD_FAILED, PLEASE_ABORT);
            expectedActions.put(DATA_DOWNLOADING, PLEASE_CONTINUE);
            expectedActions.put(DATA_DOWNLOADED, PLEASE_COMPUTE);
            expectedActions.put(DATA_DOWNLOAD_FAILED, PLEASE_ABORT);
            expectedActions.put(COMPUTING, PLEASE_CONTINUE);
            expectedActions.put(COMPUTED, PLEASE_CONTRIBUTE);
            expectedActions.put(COMPUTE_FAILED, PLEASE_ABORT);
            expectedActions.put(CONTRIBUTING, PLEASE_CONTINUE);
            expectedActions.put(CONTRIBUTED, PLEASE_WAIT);
            expectedActions.put(CONTRIBUTE_FAILED, PLEASE_ABORT);
            expectedActions.put(REVEALING, PLEASE_CONTINUE);
            expectedActions.put(REVEALED, PLEASE_WAIT);
            expectedActions.put(REVEAL_FAILED, PLEASE_ABORT);
            expectedActions.put(RESULT_UPLOADING, PLEASE_CONTINUE);
            expectedActions.put(RESULT_UPLOADED, PLEASE_WAIT);
            expectedActions.put(RESULT_UPLOAD_FAILED, PLEASE_ABORT);
            expectedActions.put(CONTRIBUTE_AND_FINALIZE_ONGOING, PLEASE_CONTINUE);
            expectedActions.put(CONTRIBUTE_AND_FINALIZE_DONE, PLEASE_WAIT);
            expectedActions.put(CONTRIBUTE_AND_FINALIZE_FAILED, PLEASE_ABORT);
            expectedActions.put(COMPLETING, PLEASE_CONTINUE);
            expectedActions.put(COMPLETED, PLEASE_WAIT);
            expectedActions.put(COMPLETE_FAILED, PLEASE_ABORT);

            assertThat(actualActions).isEqualTo(expectedActions);
        } finally {
            folder.delete();
        }
    }
}
