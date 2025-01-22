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

package com.iexec.core.task.update;

import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpiringMap;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
class TaskUpdateRequestManagerTests {

    public static final String CHAIN_TASK_ID = "chainTaskId";

    @Mock
    private TaskService taskService;
    @Mock
    private TaskUpdateManager taskUpdateManager;

    @InjectMocks
    private TaskUpdateRequestManager taskUpdateRequestManager;

    // region publishRequest()
    @Test
    void shouldPublishRequest(CapturedOutput output) {
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID))
                .thenReturn(Optional.of(Task.builder().chainTaskId(CHAIN_TASK_ID).build()));

        final boolean publishRequestStatus = taskUpdateRequestManager.publishRequest(CHAIN_TASK_ID);
        await().atMost(5L, TimeUnit.SECONDS)
                .until(() -> output.getOut().contains("Acquire lock for task update [chainTaskId:chainTaskId]")
                        && output.getOut().contains("Release lock for task update [chainTaskId:chainTaskId]"));

        assertThat(publishRequestStatus).isTrue();
        verify(taskUpdateManager).updateTask(CHAIN_TASK_ID);
    }

    @Test
    void shouldPublishRequestButNotAcquireLock(CapturedOutput output) {
        final ExpiringMap<String, Semaphore> locks = ExpiringMap.builder()
                .expiration(30L, TimeUnit.SECONDS)
                .build();
        locks.putIfAbsent(CHAIN_TASK_ID, new Semaphore(1));
        assertThat(locks.get(CHAIN_TASK_ID).tryAcquire()).isTrue();
        ReflectionTestUtils.setField(taskUpdateRequestManager, "taskExecutionLockRunner", locks);
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID))
                .thenReturn(Optional.of(Task.builder().chainTaskId(CHAIN_TASK_ID).build()));

        final boolean publishRequestStatus = taskUpdateRequestManager.publishRequest(CHAIN_TASK_ID);
        await().atMost(5L, TimeUnit.SECONDS)
                .until(() -> output.getOut().contains("Could not acquire lock for task update [chainTaskId:chainTaskId]"));

        assertThat(publishRequestStatus).isTrue();
        verifyNoInteractions(taskUpdateManager);
    }

    @Test
    void shouldNotPublishRequestSinceEmptyTaskId() {
        final boolean publishRequestStatus = taskUpdateRequestManager.publishRequest("");

        assertThat(publishRequestStatus).isFalse();
        verifyNoInteractions(taskService, taskUpdateManager);
    }

    @Test
    void shouldNotPublishRequestSinceItemAlreadyAdded() {
        taskUpdateRequestManager.queue.add(
                buildTaskUpdate(CHAIN_TASK_ID, null, null, null)
        );

        final boolean publishRequestStatus = taskUpdateRequestManager.publishRequest(CHAIN_TASK_ID);

        assertThat(publishRequestStatus).isFalse();
        verifyNoInteractions(taskService, taskUpdateManager);
    }

    @Test
    void shouldNotPublishRequestSinceTaskDoesNotExist() {
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());

        final boolean publishRequestStatus = taskUpdateRequestManager.publishRequest(CHAIN_TASK_ID);

        assertThat(publishRequestStatus).isFalse();
        verifyNoInteractions(taskUpdateManager);
    }
    // endregion

    // region consume tasks in order
    @Test
    void shouldNotUpdateAtTheSameTime() {
        final ConcurrentLinkedQueue<Integer> callsOrder = new ConcurrentLinkedQueue<>();
        final ConcurrentHashMap<Integer, String> taskForUpdateId = new ConcurrentHashMap<>();
        final int callsPerUpdate = 10;

        final Random random = new Random();
        // Consuming a task update should only log the call a few times, while sleeping between each log
        // so that another task could be updated at the same time if authorized by lock.
        final ConcurrentMap<String, Object> locks = ExpiringMap.builder()
                .expiration(100, TimeUnit.HOURS)
                .build();
        final Consumer<String> taskUpdater = chainTaskId -> {
            synchronized (locks.computeIfAbsent(chainTaskId, key -> new Object())) {
                final int updateId = (int) System.nanoTime() % Integer.MAX_VALUE;
                taskForUpdateId.put(updateId, chainTaskId);
                for (int i = 0; i < callsPerUpdate; i++) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(random.nextInt(10));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    callsOrder.add(updateId);
                }
            }
        };

        final List<TaskUpdate> updates = Stream.of("1", "1", "2", "2", "1")
                .map(id -> buildTaskUpdate(id, TaskStatus.RUNNING, new Date(), taskUpdater))
                .toList();

        updates.forEach(taskUpdateRequestManager.taskUpdateExecutor::execute);
        await().timeout(30, TimeUnit.SECONDS)
                .until(() -> callsOrder.size() == callsPerUpdate * updates.size());

        assertThat(callsOrder).hasSize(callsPerUpdate * updates.size());

        // We loop through calls order and see if all calls for a given update have finished
        // before another update starts for this task.
        // Two updates for different tasks can run at the same time.
        Map<String, Map<Integer, Integer>> foundTaskUpdates = new HashMap<>();

        for (int updateId : callsOrder) {
            log.info("[taskId:{}, updateId:{}]", taskForUpdateId.get(updateId), updateId);
            final Map<Integer, Integer> foundOutputsForKeyGroup = foundTaskUpdates.computeIfAbsent(taskForUpdateId.get(updateId), key -> new HashMap<>());
            for (int alreadyFound : foundOutputsForKeyGroup.keySet()) {
                if (!Objects.equals(alreadyFound, updateId) && foundOutputsForKeyGroup.get(alreadyFound) < callsPerUpdate) {
                    Assertions.fail("Synchronization has failed: %s has only %s out of %s occurrences while %s has been inserted.",
                            alreadyFound, foundOutputsForKeyGroup.get(alreadyFound), callsPerUpdate, updateId);
                }
            }

            foundOutputsForKeyGroup.merge(updateId, 1, (currentValue, defaultValue) -> currentValue + 1);
        }
    }
    // endregion

    // region queue ordering
    @Test
    void shouldGetInOrderForStatus() {
        final LinkedBlockingQueue<Runnable> queue = taskUpdateRequestManager.queue;

        TaskUpdate initializingTask = buildTaskUpdate(null, TaskStatus.INITIALIZING, null, null);
        TaskUpdate completedTask = buildTaskUpdate(null, TaskStatus.COMPLETED, null, null);
        TaskUpdate runningTask = buildTaskUpdate(null, TaskStatus.RUNNING, null, null);
        TaskUpdate initializedTask = buildTaskUpdate(null, TaskStatus.INITIALIZED, null, null);
        TaskUpdate consensusReachedTask = buildTaskUpdate(null, TaskStatus.CONSENSUS_REACHED, null, null);

        List<TaskUpdate> tasks = new ArrayList<>(
                List.of(
                        initializingTask,
                        completedTask,
                        runningTask,
                        initializedTask,
                        consensusReachedTask
                )
        );
        Collections.shuffle(tasks);
        queue.addAll(tasks);

        for (final TaskUpdate taskUpdate : tasks) {
            assertThat(queue.poll()).isEqualTo(taskUpdate);
        }

    }

    @Test
    void shouldGetInOrderForContributionDeadline() {
        final LinkedBlockingQueue<Runnable> queue = taskUpdateRequestManager.queue;

        final Date d1 = new GregorianCalendar(2021, Calendar.JANUARY, 1).getTime();
        final Date d2 = new GregorianCalendar(2021, Calendar.JANUARY, 2).getTime();
        final Date d3 = new GregorianCalendar(2021, Calendar.JANUARY, 3).getTime();
        final Date d4 = new GregorianCalendar(2021, Calendar.JANUARY, 4).getTime();
        final Date d5 = new GregorianCalendar(2021, Calendar.JANUARY, 5).getTime();

        TaskUpdate t1 = buildTaskUpdate(null, TaskStatus.RUNNING, d1, null);
        TaskUpdate t2 = buildTaskUpdate(null, TaskStatus.RUNNING, d2, null);
        TaskUpdate t3 = buildTaskUpdate(null, TaskStatus.RUNNING, d3, null);
        TaskUpdate t4 = buildTaskUpdate(null, TaskStatus.RUNNING, d4, null);
        TaskUpdate t5 = buildTaskUpdate(null, TaskStatus.RUNNING, d5, null);

        List<TaskUpdate> tasks = new ArrayList<>(List.of(t1, t2, t3, t4, t5));
        Collections.shuffle(tasks);
        queue.addAll(tasks);

        for (final TaskUpdate taskUpdate : tasks) {
            assertThat(queue.poll()).isEqualTo(taskUpdate);
        }
    }

    @Test
    void shouldGetInOrderForStatusAndContributionDeadline() {
        final LinkedBlockingQueue<Runnable> queue = taskUpdateRequestManager.queue;

        final Date d1 = new GregorianCalendar(2021, Calendar.JANUARY, 1).getTime();
        final Date d2 = new GregorianCalendar(2021, Calendar.JANUARY, 2).getTime();

        TaskUpdate t1 = buildTaskUpdate(null, TaskStatus.RUNNING, d1, null);
        TaskUpdate t2 = buildTaskUpdate(null, TaskStatus.RUNNING, d2, null);
        TaskUpdate t3 = buildTaskUpdate(null, TaskStatus.CONSENSUS_REACHED, d1, null);
        TaskUpdate t4 = buildTaskUpdate(null, TaskStatus.CONSENSUS_REACHED, d2, null);

        List<TaskUpdate> tasks = new ArrayList<>(List.of(t1, t2, t3, t4));
        Collections.shuffle(tasks);
        queue.addAll(tasks);

        for (final TaskUpdate taskUpdate : tasks) {
            assertThat(queue.poll()).isEqualTo(taskUpdate);
        }
    }
    // endregion

    private TaskUpdate buildTaskUpdate(String chainTaskId,
                                       TaskStatus status,
                                       Date contributionDeadline,
                                       Consumer<String> taskUpdater) {
        return new TaskUpdate(
                Task.builder()
                        .chainTaskId(chainTaskId)
                        .currentStatus(status)
                        .contributionDeadline(contributionDeadline)
                        .build(),
                taskUpdater
        );
    }
}
