package com.iexec.core.task.update;

import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import net.jodah.expiringmap.ExpiringMap;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;

class TaskUpdateRequestManagerTests {

    public static final String CHAIN_TASK_ID = "chainTaskId";

    @Mock
    private TaskService taskService;

    @Mock
    private TaskUpdateManager taskUpdateManager;

    @InjectMocks
    private TaskUpdateRequestManager taskUpdateRequestManager;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    // region publishRequest()
    @Test
    void shouldPublishRequest() throws ExecutionException, InterruptedException {
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID))
                .thenReturn(Optional.of(Task.builder().chainTaskId(CHAIN_TASK_ID).build()));

        CompletableFuture<Boolean> booleanCompletableFuture = taskUpdateRequestManager.publishRequest(CHAIN_TASK_ID);
        booleanCompletableFuture.join();

        Assertions.assertThat(booleanCompletableFuture.get()).isTrue();
    }

    @Test
    void shouldNotPublishRequestSinceEmptyTaskId() throws ExecutionException, InterruptedException {
        CompletableFuture<Boolean> booleanCompletableFuture = taskUpdateRequestManager.publishRequest("");
        booleanCompletableFuture.join();

        Assertions.assertThat(booleanCompletableFuture.get()).isFalse();
    }

    @Test
    void shouldNotPublishRequestSinceItemAlreadyAdded() throws ExecutionException, InterruptedException {
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID))
                .thenReturn(Optional.of(Task.builder().chainTaskId(CHAIN_TASK_ID).build()));
        taskUpdateRequestManager.queue.add(
                buildTaskUpdate(CHAIN_TASK_ID, null, null, null)
        );

        CompletableFuture<Boolean> booleanCompletableFuture = taskUpdateRequestManager.publishRequest(CHAIN_TASK_ID);
        booleanCompletableFuture.join();

        Assertions.assertThat(booleanCompletableFuture.get()).isFalse();
    }

    @Test
    void shouldNotPublishRequestSinceTaskDoesNotExist() throws ExecutionException, InterruptedException {
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());

        CompletableFuture<Boolean> booleanCompletableFuture = taskUpdateRequestManager.publishRequest(CHAIN_TASK_ID);
        booleanCompletableFuture.join();

        Assertions.assertThat(booleanCompletableFuture.get()).isFalse();
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
                .collect(Collectors.toList());

        updates.forEach(taskUpdateRequestManager.taskUpdateExecutor::execute);
        Awaitility
                .await()
                .timeout(30, TimeUnit.SECONDS)
                .until(() -> callsOrder.size() == callsPerUpdate * updates.size());

        Assertions.assertThat(callsOrder.size()).isEqualTo(callsPerUpdate * updates.size());

        // We loop through calls order and see if all calls for a given update have finished
        // before another update starts for this task.
        // Two updates for different tasks can run at the same time.
        Map<String, Map<Integer, Integer>> foundTaskUpdates = new HashMap<>();

        for (int updateId : callsOrder) {
            System.out.println("[taskId:" + taskForUpdateId.get(updateId) + ", updateId:" + updateId + "]");
            final Map<Integer, Integer> foundOutputsForKeyGroup = foundTaskUpdates.computeIfAbsent(taskForUpdateId.get(updateId), (key) -> new HashMap<>());
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
    void shouldGetInOrderForStatus() throws InterruptedException {
        final TaskUpdatePriorityBlockingQueue queue = taskUpdateRequestManager.queue;

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

        final List<TaskUpdate> prioritizedTasks = queue.takeAll();
        Assertions.assertThat(prioritizedTasks)
                .containsExactly(
                        completedTask,
                        consensusReachedTask,
                        runningTask,
                        initializedTask,
                        initializingTask
                );
    }

    @Test
    void shouldGetInOrderForContributionDeadline() throws InterruptedException {
        final TaskUpdatePriorityBlockingQueue queue = taskUpdateRequestManager.queue;

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

        final List<TaskUpdate> prioritizedTasks = queue.takeAll();
        Assertions.assertThat(prioritizedTasks)
                .containsExactly(
                        t1,
                        t2,
                        t3,
                        t4,
                        t5
                );
    }

    @Test
    void shouldGetInOrderForStatusAndContributionDeadline() throws InterruptedException {
        final TaskUpdatePriorityBlockingQueue queue = taskUpdateRequestManager.queue;

        final Date d1 = new GregorianCalendar(2021, Calendar.JANUARY, 1).getTime();
        final Date d2 = new GregorianCalendar(2021, Calendar.JANUARY, 2).getTime();

        TaskUpdate t1 = buildTaskUpdate(null, TaskStatus.RUNNING, d1, null);
        TaskUpdate t2 = buildTaskUpdate(null, TaskStatus.RUNNING, d2, null);
        TaskUpdate t3 = buildTaskUpdate(null, TaskStatus.CONSENSUS_REACHED, d1, null);
        TaskUpdate t4 = buildTaskUpdate(null, TaskStatus.CONSENSUS_REACHED, d2, null);

        List<TaskUpdate> tasks = new ArrayList<>(List.of(t1, t2, t3, t4));
        Collections.shuffle(tasks);
        queue.addAll(tasks);

        final List<TaskUpdate> prioritizedTasks = queue.takeAll();
        Assertions.assertThat(prioritizedTasks)
                .containsExactly(
                        t3,
                        t4,
                        t1,
                        t2
                );
    }
    // endregion
    
    private TaskUpdate buildTaskUpdate(String chainTaskId,
                                       TaskStatus status,
                                       Date contributionDeadline,
                                       Consumer<String> taskUpdater) {
        return new TaskUpdate(
                Task
                        .builder()
                        .chainTaskId(chainTaskId)
                        .currentStatus(status).
                        contributionDeadline(contributionDeadline).
                        build(),
                taskUpdater
        );
    }
}
