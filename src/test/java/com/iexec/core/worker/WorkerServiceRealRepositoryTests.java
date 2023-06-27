package com.iexec.core.worker;

import com.iexec.core.configuration.WorkerConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.iexec.commons.poco.utils.TestUtils.WALLET_WORKER_1;

@Slf4j
@DataMongoTest
@Testcontainers
class WorkerServiceRealRepositoryTests {
    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.2"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.host", mongoDBContainer::getContainerIpAddress);
        registry.add("spring.data.mongodb.port", () -> mongoDBContainer.getMappedPort(27017));
    }

    @SpyBean
    private WorkerRepository workerRepository;
    @Mock
    private WorkerConfiguration workerConfiguration;
    private WorkerService workerService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        workerService = new WorkerService(workerRepository, workerConfiguration);
    }

    /**
     * Try and add N tasks to a single worker at the same time.
     * If everything goes right, the Worker should finally have been assigned N tasks.
     */
    @Test
    void addMultipleTaskIds() throws ExecutionException, InterruptedException {
        workerService.addWorker(
                Worker.builder()
                        .walletAddress(WALLET_WORKER_1)
                        .build()
        );

        final int nThreads = 10;
        final ExecutorService executor = Executors.newFixedThreadPool(nThreads);

        final List<Future<Optional<Worker>>> futures = IntStream.range(0, nThreads)
                .mapToObj(i -> executor.submit(() -> workerService.addChainTaskIdToWorker(new Date().getTime() + "", WALLET_WORKER_1)))
                .collect(Collectors.toList());

        for (Future<Optional<Worker>> future : futures) {
            future.get();
        }

        Assertions.assertThat(workerService.getWorker(WALLET_WORKER_1).get().getComputingChainTaskIds())
                .hasSize(nThreads);
    }
}
