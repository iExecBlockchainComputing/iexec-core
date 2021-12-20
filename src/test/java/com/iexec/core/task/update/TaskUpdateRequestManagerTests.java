package com.iexec.core.task.update;

import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;

class TaskUpdateRequestManagerTests {

    public static final String CHAIN_TASK_ID = "chainTaskId";

    @Mock
    private TaskService taskService;

    @InjectMocks
    private TaskUpdateRequestManager taskUpdateRequestManager;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    // region publishRequest()
    @Test
    void shouldPublishRequest() throws ExecutionException, InterruptedException {
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID))
                .thenReturn(Optional.of(new Task()));

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
        taskUpdateRequestManager.publishRequest(CHAIN_TASK_ID);

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

    // region consumeAndNotify()
    @Test
    void shouldNotUpdateAtTheSameTime() throws NoSuchFieldException, IllegalAccessException {
        final ConcurrentLinkedQueue<Integer> callsOrder = new ConcurrentLinkedQueue<>();
        final ConcurrentHashMap<Integer, String> taskForUpdateId = new ConcurrentHashMap<>();
        final int callsPerUpdate = 10;
        final List<Task> updates = Stream.of("1", "1", "2", "2", "1")
                .map(id -> Task
                        .builder()
                        .chainTaskId(id)
                        .currentStatus(TaskStatus.RUNNING)
                        .contributionDeadline(new Date())
                        .build())
                .collect(Collectors.toList());
        final Random random = new Random();

        // Consuming a task update should only log the call a few times, while sleeping between each log
        // so that another task could be updated at the same time if authorized by lock.
        final TaskUpdateRequestConsumer consumer = chainTaskId -> {
            final int updateId = (int)System.nanoTime() % Integer.MAX_VALUE;
            taskForUpdateId.put(updateId, chainTaskId);
            for (int i = 0; i < callsPerUpdate; i++) {
                try {
                    TimeUnit.MILLISECONDS.sleep(random.nextInt(10));
                } catch (InterruptedException ignored) {}
                callsOrder.add(updateId);
            }
        };
        taskUpdateRequestManager.setRequestConsumer(consumer);

        // Ugly way to put some task updates in waiting queue.
        final Field queueField = TaskUpdateRequestManager.class
                .getDeclaredField("queue");
        queueField.setAccessible(true);
        //noinspection unchecked
        final BlockingQueue<Task> queue = (BlockingQueue<Task>) queueField.get(taskUpdateRequestManager);
        queue.addAll(updates);

        // We need to run this method as a new thread, so we can interrupt it after all tasks have run.
        final CompletableFuture<Void> asyncRun = CompletableFuture.runAsync(taskUpdateRequestManager::consumeAndNotify);

        Awaitility
                .await()
                .timeout(30, TimeUnit.SECONDS)
                .until(() -> callsOrder.size() == callsPerUpdate * updates.size());

        asyncRun.cancel(true);

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

    // region createQueue()
    @Test
    void shouldGetInOrderForStatus() {
        final PriorityBlockingQueue<Task> queue = taskUpdateRequestManager.createQueue();

        Task initializingTask = Task.builder().currentStatus(TaskStatus.INITIALIZING).build();
        Task completedTask = Task.builder().currentStatus(TaskStatus.COMPLETED).build();
        Task runningTask = Task.builder().currentStatus(TaskStatus.RUNNING).build();
        Task initializedTask = Task.builder().currentStatus(TaskStatus.INITIALIZED).build();
        Task consensusReachedTask = Task.builder().currentStatus(TaskStatus.CONSENSUS_REACHED).build();

        List<Task> tasks = new ArrayList<>(
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

        final List<Task> prioritizedTasks = new ArrayList<>();
        queue.drainTo(prioritizedTasks);

        Assertions.assertThat(prioritizedTasks)
                .containsExactlyInAnyOrder(
                        completedTask,
                        consensusReachedTask,
                        runningTask,
                        initializedTask,
                        initializingTask
                );
    }

    @Test
    void shouldGetInOrderForContributionDeadline() {
        final PriorityBlockingQueue<Task> queue = taskUpdateRequestManager.createQueue();

        final Date d1 = new GregorianCalendar(2021, Calendar.JANUARY, 1).getTime();
        final Date d2 = new GregorianCalendar(2021, Calendar.JANUARY, 2).getTime();
        final Date d3 = new GregorianCalendar(2021, Calendar.JANUARY, 3).getTime();
        final Date d4 = new GregorianCalendar(2021, Calendar.JANUARY, 4).getTime();
        final Date d5 = new GregorianCalendar(2021, Calendar.JANUARY, 5).getTime();

        Task t1 = Task.builder().currentStatus(TaskStatus.RUNNING).contributionDeadline(d1).build();
        Task t2 = Task.builder().currentStatus(TaskStatus.RUNNING).contributionDeadline(d2).build();
        Task t3 = Task.builder().currentStatus(TaskStatus.RUNNING).contributionDeadline(d3).build();
        Task t4 = Task.builder().currentStatus(TaskStatus.RUNNING).contributionDeadline(d4).build();
        Task t5 = Task.builder().currentStatus(TaskStatus.RUNNING).contributionDeadline(d5).build();

        List<Task> tasks = new ArrayList<>(List.of(t1, t2, t3, t4, t5));
        Collections.shuffle(tasks);
        System.out.println(tasks);
        queue.addAll(tasks);

        final List<Task> prioritizedTasks = new ArrayList<>();
        queue.drainTo(prioritizedTasks);
        System.out.println(prioritizedTasks);

        Assertions.assertThat(prioritizedTasks)
                .containsExactlyInAnyOrder(
                        t1,
                        t2,
                        t3,
                        t4,
                        t5
                );
    }

    @Test
    void shouldGetInOrderForStatusAndContributionDeadline() {
        final PriorityBlockingQueue<Task> queue = taskUpdateRequestManager.createQueue();

        final Date d1 = new GregorianCalendar(2021, Calendar.JANUARY, 1).getTime();
        final Date d2 = new GregorianCalendar(2021, Calendar.JANUARY, 2).getTime();

        Task t1 = Task.builder().currentStatus(TaskStatus.INITIALIZING).contributionDeadline(d2).build();
        Task t2 = Task.builder().currentStatus(TaskStatus.INITIALIZED).contributionDeadline(d2).build();
        Task t3 = Task.builder().currentStatus(TaskStatus.RUNNING).contributionDeadline(d1).build();
        Task t4 = Task.builder().currentStatus(TaskStatus.COMPLETED).contributionDeadline(d1).build();
        Task t5 = Task.builder().currentStatus(TaskStatus.CONSENSUS_REACHED).contributionDeadline(d1).build();

        List<Task> tasks = new ArrayList<>(List.of(t1, t2, t3, t4, t5));
        Collections.shuffle(tasks);
        queue.addAll(tasks);

        final List<Task> prioritizedTasks = new ArrayList<>();
        queue.drainTo(prioritizedTasks);

        Assertions.assertThat(prioritizedTasks)
                .containsExactlyInAnyOrder(
                        t3,
                        t4,
                        t5,
                        t1,
                        t2
                );
    }
    // endregion
}
