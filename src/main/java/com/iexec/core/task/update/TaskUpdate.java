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

package com.iexec.core.task.update;

import com.iexec.core.task.Task;
import com.iexec.core.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

@Slf4j
class TaskUpdate implements Runnable, Comparable<TaskUpdate> {
    private final Task task;
    private final ConcurrentMap<String, Object> locks;
    private final TaskUpdateRequestConsumer consumer;

    TaskUpdate(Task task,
               ConcurrentMap<String, Object> locks,
               TaskUpdateRequestConsumer consumer) {
        this.task = task;
        this.locks = locks;
        this.consumer = consumer;
    }

    public Task getTask() {
        return task;
    }

    public String getChainTaskId() {
        return task.getChainTaskId();
    }

    private TaskStatus getCurrentStatus() {
        return task.getCurrentStatus();
    }

    private Date getContributionDeadline() {
        return task.getContributionDeadline();
    }

    /**
     * Updates a task.
     * <br>
     * 2 updates can be run in parallel if they don't target the same task.
     * Otherwise, the second update will wait until the first one is achieved.
     */
    @Override
    public void run() {
        if (task == null) {
            return;
        }

        String chainTaskId = task.getChainTaskId();
        log.debug("Selected task [chainTaskId: {}, status: {}]", chainTaskId, task.getCurrentStatus());
        synchronized (locks.computeIfAbsent(chainTaskId, key -> new Object())) { // require one update on a same task at a time
            consumer.onTaskUpdateRequest(chainTaskId); // synchronously update task
        }
    }

    @Override
    public int compareTo(TaskUpdate otherTaskUpdate) {
        if (otherTaskUpdate == null) {
            // A `null` element should be considered
            // as the last one in an ordered collection.
            return Integer.MAX_VALUE;
        }
        return Comparator.comparing(TaskUpdate::getCurrentStatus, Comparator.reverseOrder())
                .thenComparing(TaskUpdate::getContributionDeadline)
                .compare(this, otherTaskUpdate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskUpdate that = (TaskUpdate) o;
        return Objects.equals(task, that.task);
    }

    @Override
    public int hashCode() {
        return Objects.hash(task);
    }
}
