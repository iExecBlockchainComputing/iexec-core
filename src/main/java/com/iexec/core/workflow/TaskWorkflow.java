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

import com.iexec.core.task.TaskStatus;

import static com.iexec.core.task.TaskStatus.*;


public class TaskWorkflow extends Workflow<TaskStatus> {

    private static TaskWorkflow instance;

    public static synchronized TaskWorkflow getInstance() {
        if (instance == null) {
            instance = new TaskWorkflow();
        }
        return instance;
    }

    private TaskWorkflow() {
        super();

        // This is where the whole workflow is defined
        addTransition(INITIALIZED, RUNNING);
        addTransition(RUNNING, CONSENSUS_REACHED);
        addTransition(RUNNING, RUNNING_FAILED);
        addTransition(CONSENSUS_REACHED, AT_LEAST_ONE_REVEALED);
        addTransition(AT_LEAST_ONE_REVEALED, RESULT_UPLOADING);
        addTransition(RESULT_UPLOADING, RESULT_UPLOADED);
        addTransition(RESULT_UPLOADED, COMPLETED);
        addTransition(RESULT_UPLOADING, FAILED);
    }
}
