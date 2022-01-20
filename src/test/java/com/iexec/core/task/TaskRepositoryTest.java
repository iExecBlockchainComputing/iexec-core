package com.iexec.core.task;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.iexec.core.task.TaskStatus.INITIALIZED;
import static com.iexec.core.task.TaskStatus.RUNNING;
import static com.iexec.core.task.TaskTestsUtils.getStubTask;

@DataMongoTest
@Testcontainers
class TaskRepositoryTest {

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
    private static final Random generator = new Random();

    private final long maxExecutionTime = 60000;

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.2"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.host", mongoDBContainer::getContainerIpAddress);
        registry.add("spring.data.mongodb.port", () -> mongoDBContainer.getMappedPort(27017));
    }

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void init() {
        taskRepository.deleteAll();
    }

    private String generateHexId() {
        int length = 64;
        StringBuilder sb = new StringBuilder("0x");
        for (int j = 0; j < length; j++) {
            sb.append(HEX_ARRAY[generator.nextInt(HEX_ARRAY.length)]);
        }
        return sb.toString();
    }

    private List<Task> queryTasksOrderedByStatusThenContributionDeadline() {
        return taskRepository.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING),
                Sort.by(Sort.Order.desc(Task.CURRENT_STATUS_FIELD_NAME),
                        Sort.Order.asc(Task.CONTRIBUTION_DEADLINE_FIELD_NAME))
        );
    }

    @Test
    void shouldFailWithDuplicateUniqueDealIdx() {
        Task task1 = getStubTask(maxExecutionTime);
        Task task2 = getStubTask(maxExecutionTime);
        Assertions.assertThatThrownBy(() -> taskRepository.saveAll(Arrays.asList(task1, task2)))
                .isInstanceOf(DuplicateKeyException.class)
                .hasCauseExactlyInstanceOf(com.mongodb.MongoBulkWriteException.class)
                .hasMessageContainingAll("E11000", "duplicate key error collection", "unique_deal_idx dup key");
    }

    @Test
    void shouldFailWithDuplicateChainTaskId() {
        Task task1 = getStubTask(maxExecutionTime);
        task1.setTaskIndex(0);
        Task task2 = getStubTask(maxExecutionTime);
        task2.setTaskIndex(1);
        Assertions.assertThatThrownBy(() -> taskRepository.saveAll(Arrays.asList(task1, task2)))
                .isInstanceOf(DuplicateKeyException.class)
                .hasCauseExactlyInstanceOf(com.mongodb.MongoBulkWriteException.class)
                .hasMessageContainingAll("E11000", "duplicate key error collection", "chainTaskId dup key");
    }

    @Test
    void shouldFindTasksOrderedByCurrentStatusAndContributionDeadline() {
        Task task1 = getStubTask(maxExecutionTime);
        task1.setChainTaskId(generateHexId());
        task1.setChainDealId(generateHexId());
        task1.setCurrentStatus(RUNNING);
        task1.setContributionDeadline(Date.from(Instant.now().plus(20, ChronoUnit.MINUTES)));

        Task task2 = getStubTask(maxExecutionTime);
        task2.setChainDealId(generateHexId());
        task2.setChainTaskId(generateHexId());
        task2.setCurrentStatus(INITIALIZED);
        task2.setContributionDeadline(Date.from(Instant.now().plus(20, ChronoUnit.MINUTES)));

        Task task3 = getStubTask(maxExecutionTime);
        task3.setChainDealId(generateHexId());
        task3.setChainTaskId(generateHexId());
        task3.setCurrentStatus(INITIALIZED);
        task3.setContributionDeadline(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)));

        Task task4 = getStubTask(maxExecutionTime);
        task4.setChainDealId(generateHexId());
        task4.setChainTaskId(generateHexId());
        task4.setCurrentStatus(RUNNING);
        task4.setContributionDeadline(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)));

        taskRepository.saveAll(Arrays.asList(task1, task2, task3, task4));

        List<Task> foundTasks = queryTasksOrderedByStatusThenContributionDeadline();
        Assertions.assertThat(foundTasks.size()).isEqualTo(4);
        Assertions.assertThat(foundTasks.remove(0)).usingRecursiveComparison().isEqualTo(task4);
        Assertions.assertThat(foundTasks.remove(0)).usingRecursiveComparison().isEqualTo(task1);
        Assertions.assertThat(foundTasks.remove(0)).usingRecursiveComparison().isEqualTo(task3);
        Assertions.assertThat(foundTasks.remove(0)).usingRecursiveComparison().isEqualTo(task2);
    }

    @Test
    void shouldFindTasksOrderedByCurrentStatusAndContributionDeadlineWithFuzzyData() {
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Task task = getStubTask(maxExecutionTime);
            task.setChainDealId(generateHexId());
            task.setChainTaskId(generateHexId());
            task.setCurrentStatus(generator.nextInt(50) % 2 == 0 ? RUNNING : INITIALIZED);
            int amountToAdd = generator.nextInt(10);
            task.setContributionDeadline(Date.from(Instant.now().plus(amountToAdd, ChronoUnit.MINUTES)));
            tasks.add(task);
        }
        taskRepository.saveAll(tasks);
        tasks.sort(Comparator.comparing(Task::getCurrentStatus, Comparator.reverseOrder())
                .thenComparing(Task::getContributionDeadline));

        List<Task> foundTasks = queryTasksOrderedByStatusThenContributionDeadline();
        Assertions.assertThat(foundTasks.size()).isEqualTo(taskRepository.count());
        for (Task task : tasks) {
            Assertions.assertThat(task).usingRecursiveComparison().isEqualTo(foundTasks.remove(0));
        }
    }

}
