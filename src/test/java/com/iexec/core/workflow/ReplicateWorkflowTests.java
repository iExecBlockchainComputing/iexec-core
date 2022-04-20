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

package com.iexec.core.workflow;

import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplicateWorkflowTests {

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
                .isEqualTo(TaskNotificationType.PLEASE_ABORT);
    }

    @Test
    void shouldGetNextActionOnAppDownloadFailedWithPostComputeFailed(){
        assertThat(replicateWorkflow
                .getNextAction(ReplicateStatus.APP_DOWNLOAD_FAILED,
                        ReplicateStatusCause.POST_COMPUTE_UNKNOWN_ISSUE))
                .isEqualTo(TaskNotificationType.PLEASE_ABORT);
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
                .isEqualTo(TaskNotificationType.PLEASE_ABORT);
    }

    @Test
    void shouldGetNextActionOnDataDownloadFailedWithPostComputeFailed(){
        assertThat(replicateWorkflow
                .getNextAction(ReplicateStatus.DATA_DOWNLOAD_FAILED,
                        ReplicateStatusCause.POST_COMPUTE_UNKNOWN_ISSUE))
                .isEqualTo(TaskNotificationType.PLEASE_ABORT);
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

}
