package com.iexec.core.worker;

import org.junit.Before;
import org.junit.Test;
import org.mockito.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class WorkerServiceTests {

    @Mock
    private WorkerRepository workerRepository;

    @InjectMocks
    private WorkerService workerService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotAddNewWorker() {
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .lastAliveDate(new Date())
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        Worker addedWorker = workerService.addWorker(existingWorker);
        assertThat(addedWorker).isEqualTo(existingWorker);
    }

    @Test
    public void shouldAddNewWorker() {
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        Worker worker = Worker.builder()
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .lastAliveDate(new Date())
                .build();
        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.empty());
        when(workerRepository.save(Mockito.any())).thenReturn(worker);

        Worker addedWorker = workerService.addWorker(worker);
        // check that the save method was called once
        Mockito.verify(workerRepository, Mockito.times(1)).save(Mockito.any());
        assertThat(addedWorker.getName()).isEqualTo(worker.getName());
        assertThat(addedWorker.getLastAliveDate()).isEqualTo(worker.getLastAliveDate());
    }

    @Test
    public void shouldUpdateLastAlive() throws ParseException {
        // init
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        Date oldLastAlive = new SimpleDateFormat("yyyy-MM-dd").parse("2018-01-01");
        Worker worker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .lastAliveDate(oldLastAlive)
                .build();
        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(worker));

        // call
        Optional<Worker> updatedWorker = workerService.updateLastAlive(walletAddress);

        // check argument passed to the save method
        ArgumentCaptor<Worker> argument = ArgumentCaptor.forClass(Worker.class);
        Mockito.verify(workerRepository).save(argument.capture());
        assertThat(argument.getValue().getId()).isEqualTo(worker.getId());
        assertThat(argument.getValue().getName()).isEqualTo(worker.getName());
        // check that the save method was called with a lastAlive parameter updated less than a second ago
        Date now = new Date();
        long duration = now.getTime() - argument.getValue().getLastAliveDate().getTime();
        long diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
        assertThat(diffInSeconds).isEqualTo(0);

        // check object returned by the method
        assertThat(updatedWorker.isPresent()).isTrue();
        assertThat(updatedWorker.get().getId()).isEqualTo(worker.getId());
        assertThat(updatedWorker.get().getName()).isEqualTo(worker.getName());
        duration = now.getTime() - updatedWorker.get().getLastAliveDate().getTime();
        diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
        assertThat(diffInSeconds).isEqualTo(0);
    }


    @Test
    public void shouldNotFindWorkerForUpdateLastAlive() {
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.empty());

        Optional<Worker> optional = workerService.updateLastAlive(walletAddress);
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    public void shouldGetWorker() {
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .lastAliveDate(new Date())
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        Optional<Worker> foundWorker = workerService.getWorker(walletAddress);
        assertThat(foundWorker.isPresent()).isTrue();
        assertThat(foundWorker.get()).isEqualTo(existingWorker);
    }

    @Test
    public void shouldAddTaskIdToWorker(){
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        List<String> listIds = new ArrayList<>();
        listIds.add("task1");
        listIds.add("task2");
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .lastAliveDate(new Date())
                .taskIds(listIds)
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(existingWorker)).thenReturn(existingWorker);

        Optional<Worker> addedWorker = workerService.addTaskIdToWorker("task3", walletAddress);
        assertThat(addedWorker.isPresent()).isTrue();
        Worker worker = addedWorker.get();
        assertThat(worker.getTaskIds().size()).isEqualTo(3);
        assertThat(worker.getTaskIds().get(2)).isEqualTo("task3");
    }

    @Test
    public void shouldNotAddTaskIdToWorker(){
        when(workerRepository.findByWalletAddress(Mockito.anyString())).thenReturn(Optional.empty());
        Optional<Worker> addedWorker = workerService.addTaskIdToWorker("task1", "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248");
        assertThat(addedWorker.isPresent()).isFalse();
    }

    @Test
    public void shouldRemoveTaskIdFromWorker(){
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        List<String> listIds = new ArrayList<>();
        listIds.add("task1");
        listIds.add("task2");
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .lastAliveDate(new Date())
                .taskIds(listIds)
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(existingWorker)).thenReturn(existingWorker);

        Optional<Worker> removedWorker = workerService.removeTaskIdFromWorker("task2", walletAddress);
        assertThat(removedWorker.isPresent()).isTrue();
        Worker worker = removedWorker.get();
        assertThat(worker.getTaskIds().size()).isEqualTo(1);
        assertThat(worker.getTaskIds().get(0)).isEqualTo("task1");
    }

    @Test
    public void shouldNotRemoveTaskIdWorkerNotFound(){
        when(workerRepository.findByWalletAddress(Mockito.anyString())).thenReturn(Optional.empty());
        Optional<Worker> addedWorker = workerService.removeTaskIdFromWorker("task1", "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248");
        assertThat(addedWorker.isPresent()).isFalse();
    }

    @Test
    public void shouldNotRemoveAnythingSinceTaskIdNotFound(){
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        List<String> listIds = new ArrayList<>();
        listIds.add("task1");
        listIds.add("task2");
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .lastAliveDate(new Date())
                .taskIds(listIds)
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(existingWorker)).thenReturn(existingWorker);

        Optional<Worker> removedWorker = workerService.removeTaskIdFromWorker("dummyTaskId", walletAddress);
        assertThat(removedWorker.isPresent()).isTrue();
        Worker worker = removedWorker.get();
        assertThat(worker.getTaskIds().size()).isEqualTo(2);
        assertThat(worker.getTaskIds()).isEqualTo(listIds);
    }
}
