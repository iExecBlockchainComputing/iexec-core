/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.task.event;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.context.ApplicationEvent;

@Value
@EqualsAndHashCode(callSuper = true)
public class TaskCreatedEvent extends ApplicationEvent {
    String chainTaskId;
    String chainDealId;
    int taskIndex;

    public TaskCreatedEvent(final Object source, final String chainTaskId, final String chainDealId, final int taskIndex) {
        super(source);
        this.chainTaskId = chainTaskId;
        this.chainDealId = chainDealId;
        this.taskIndex = taskIndex;
    }
}
