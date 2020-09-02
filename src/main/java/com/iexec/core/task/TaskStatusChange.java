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

package com.iexec.core.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.Date;

import com.iexec.common.chain.ChainReceipt;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskStatusChange {

    private Date date;
    private TaskStatus status;
    private ChainReceipt chainReceipt;

    TaskStatusChange(TaskStatus status){
        this(status, null);
    }

    TaskStatusChange(TaskStatus status, ChainReceipt chainReceipt){
        this.date = new Date();
        this.status = status;
        this.chainReceipt = chainReceipt;
    }

    public TaskStatusChange(Date date, TaskStatus status) {
        this(date, status, null);
    }
}
