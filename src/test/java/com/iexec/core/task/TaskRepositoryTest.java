package com.iexec.core.task;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.iexec.core.task.TaskStatus.INITIALIZED;
import static com.iexec.core.task.TaskStatus.RUNNING;
import static com.iexec.core.task.TaskTestsUtils.getStubTask;

@DataMongoTest
@Testcontainers
class TaskRepositoryTest {

    private final long maxExecutionTime = 60000;

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.2"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void shouldFindTasksOrderedByContributionDeadline() {
        Task task1 = getStubTask(maxExecutionTime);
        task1.setChainDealId("0x1");
        task1.setChainTaskId("0x1");
        task1.setCurrentStatus(INITIALIZED);
        task1.setContributionDeadline(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)));

        Task task2 = getStubTask(maxExecutionTime);
        task2.setChainDealId("0x2");
        task2.setChainTaskId("0x2");
        task2.setCurrentStatus(RUNNING);
        task2.setContributionDeadline(Date.from(Instant.now().plus(3, ChronoUnit.MINUTES)));

        taskRepository.saveAll(Arrays.asList(task1, task2));

        List<Task> foundTasks = taskRepository.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING),
                Sort.by(Sort.Direction.ASC, "contributionDeadline"));
        Assertions.assertThat(foundTasks.size()).isEqualTo(taskRepository.count());
        Assertions.assertThat(foundTasks.get(0)).isEqualToComparingFieldByField(task2);
        Assertions.assertThat(foundTasks.get(1)).isEqualToComparingFieldByField((task1));
    }

}
