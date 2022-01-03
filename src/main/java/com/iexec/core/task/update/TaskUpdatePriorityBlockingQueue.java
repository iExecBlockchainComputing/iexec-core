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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Stream;

public class TaskUpdatePriorityBlockingQueue extends PriorityBlockingQueue<Runnable> {
    /**
     * Inserts the specified element into this priority queue.
     * As the queue is unbounded, this method will never return {@code false}.
     * <br>
     * However, if the parameter is not a {@link TaskUpdate} element,
     * it will throw an {@link UnsupportedOperationException}.
     *
     * @param runnable The element to add
     * @return {@code true} (as specified by {@link Collection#add})
     */
    @Override
    public boolean add(Runnable runnable) {
        return this.offer(runnable);
    }

    /**
     * Inserts the specified element into this priority queue.
     * As the queue is unbounded, this method will never return {@code false}.
     * <br>
     * However, if the parameter is not a {@link TaskUpdate},
     * it will throw an {@link UnsupportedOperationException}.
     *
     * @param runnable The element to add
     * @return {@code true} (as specified by {@link Queue#offer})
     */
    @Override
    public boolean offer(Runnable runnable) {
        if (!(runnable instanceof TaskUpdate)) {
            throw new UnsupportedOperationException("TaskUpdatePriorityBlockingQueue can only have TaskUpdate elements." +
                    " [addedElementClass: " + runnable.getClass().getSimpleName() + "]");
        }
        return super.offer(runnable);
    }

    @Override
    public TaskUpdate take() throws InterruptedException {
        return (TaskUpdate) super.take();
    }

    /**
     * Takes all the elements from this queue
     * and returns them in a sorted {@code List} according to their priority.
     *
     * @return A {@link List} containing all the elements of the queue,
     * sorted by priority.
     * @throws InterruptedException if interrupted while waiting
     */
    public List<TaskUpdate> takeAll() throws InterruptedException {
        List<TaskUpdate> elements = new ArrayList<>();
        while (!isEmpty()) {
            elements.add(take());
        }
        return elements;
    }

    /**
     * Returns a {@link Stream} over this collection,
     * whose elements are already cast to {@link TaskUpdate}.
     *
     * @return A {@link Stream} as described above.
     */
    public Stream<TaskUpdate> streamAsTaskUpdate() {
        return this.stream().map(TaskUpdate.class::cast);
    }

    /**
     * Checks whether a {@link Task} whose {@code chainTaskId} is equal
     * to given {@code chainTaskId} is already present in this queue.
     *
     * @param chainTaskId {@code chainTaskId} to check
     * @return {@code true} if this queue contains a {@link Task}
     * whose {@code chainTaskId} is equal to the parameter,
     * {@code false} otherwise.
     */
    public boolean containsTask(String chainTaskId) {
        return streamAsTaskUpdate().anyMatch(taskUpdate -> chainTaskId.equals(taskUpdate.getChainTaskId()));
    }
}
