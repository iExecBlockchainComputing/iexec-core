package com.iexec.core.task;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.replicate.Replicate;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class TaskServiceTests {

	@Mock
	private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

	@Test
	public void shouldGetNothing() {
        when(taskRepository.findById("dummyId")).thenReturn(Optional.empty());
        Optional<Task> task = taskService.getTask("dummyId");
        assertThat(task.isPresent()).isFalse();
	}

	@Test
    public void shouldGetOneResult(){
        Task task = Task.builder()
                .id("realId")
                .currentStatus(TaskStatus.CREATED)
                .commandLine("commandLine")
                .nbContributionNeeded(2)
                .build();
        when(taskRepository.findById("realId")).thenReturn(Optional.of(task));
        Optional<Task> optional = taskService.getTask("realId");
        assertThat(optional.isPresent()).isTrue();
        assertThat(optional.get()).isEqualTo(task);
    }

    @Test
    public void shouldAddTask(){
        Task task = Task.builder()
                .id("realId")
                .currentStatus(TaskStatus.CREATED)
                .dappName("dappName")
                .commandLine("commandLine")
                .nbContributionNeeded(2)
                .build();
        when(taskRepository.save(any())).thenReturn(task);
        Task saved = taskService.addTask("dappName", "commandLine", 2);
        assertThat(saved).isNotNull();
        assertThat(saved).isEqualTo(task);
    }

    @Test
    public void shouldUpdateReplicateStatus() {
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("worker1", "taskId"));

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.CREATED));

        Task task = Task.builder()
                .id("taskId")
                .currentStatus(TaskStatus.CREATED)
                .commandLine("ls")
                .nbContributionNeeded(1)
                .replicates(replicates)
                .dateStatusList(dateStatusList)
                .build();

        when(taskRepository.findById("taskId")).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);
        Optional<Replicate> updated = taskService.updateReplicateStatus("taskId", "worker1", ReplicateStatus.RUNNING);
        assertThat(updated.isPresent()).isTrue();
        assertEquals(2, updated.get().getStatusChangeList().size());
        assertThat(updated.get().getStatusChangeList().get(0).getStatus()).isEqualTo(ReplicateStatus.CREATED);
        assertThat(updated.get().getStatusChangeList().get(1).getStatus()).isEqualTo(ReplicateStatus.RUNNING);

    }

}
