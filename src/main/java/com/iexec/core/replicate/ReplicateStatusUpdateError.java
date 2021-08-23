/*
 * Copyright 2021 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.replicate;

public enum ReplicateStatusUpdateError {
    UNKNOWN_REPLICATE("Cannot update replicate, could not get replicate [chainTaskId:{}, UpdateRequest:{}]"),
    BAD_WORKFLOW_TRANSITION("Cannot update replicate, bad workflow transition {}"),
    ALREADY_REPORTED("Cannot update replicate, status {} already reported."),
    GENERIC_CANT_UPDATE("Cannot update replicate [UpdateRequest:{}]");

    private final String errorMessageTemplate;

    ReplicateStatusUpdateError(String errorMessageTemplate) {
        this.errorMessageTemplate = errorMessageTemplate;
    }

    public String getErrorMessageTemplate() {
        return errorMessageTemplate;
    }
}
