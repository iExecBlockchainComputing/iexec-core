package com.iexec.core.task;


import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.replicate.Replicate;
import org.junit.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class TaskTests {

    @Test
    public void shouldInitializeProperly(){
        Task task = new Task("dappName", "cmdLine", 2);

        assertThat(task.getDateStatusList().size()).isEqualTo(1);
        assertThat(task.getDateStatusList().get(0).getStatus()).isEqualTo(TaskStatus.CREATED);
        assertThat(task.getReplicates()).isEmpty();
    }

    @Test
    public void shouldSetCurrentStatus() {
        Task task = new Task("dappName", "cmdLine", 2);
        assertThat(task.getDateStatusList().size()).isEqualTo(1);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.CREATED);

        task.setCurrentStatus(TaskStatus.RUNNING);
        assertThat(task.getDateStatusList().size()).isEqualTo(2);
        assertThat(task.getDateStatusList().get(0).getStatus()).isEqualTo(TaskStatus.CREATED);
        assertThat(task.getDateStatusList().get(1).getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RUNNING);

        task.setCurrentStatus(TaskStatus.COMPLETED);
        assertThat(task.getDateStatusList().size()).isEqualTo(3);
        assertThat(task.getDateStatusList().get(2).getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    public void shouldCreateNewReplicate(){
        Task task = new Task("dappName", "cmdLine", 2);
        assertThat(task.getReplicates()).isEmpty();

        String worker1 = "worker1";
        String worker2 = "worker2";
        task.createNewReplicate(worker1);
        assertThat(task.getReplicates().size()).isEqualTo(1);
        assertThat(task.getReplicates().get(0).getWorkerName()).isEqualTo(worker1);

        task.createNewReplicate(worker2);
        assertThat(task.getReplicates().size()).isEqualTo(2);
        assertThat(task.getReplicates().get(1).getWorkerName()).isEqualTo(worker2);
    }

    @Test
    public void shouldGetExistingReplicate(){
        Task task = new Task("dappName", "cmdLine", 2);
        task.createNewReplicate("worker1");
        task.createNewReplicate("worker2");
        task.createNewReplicate("worker3");

        Optional<Replicate> optional = task.getReplicate("worker2");
        assertThat(optional.isPresent()).isTrue();
        assertThat(optional.get().getWorkerName()).isEqualTo("worker2");
    }

    @Test
    public void shouldNotGetAnyReplicate(){
        Task task = new Task("dappName", "cmdLine", 2);
        task.createNewReplicate("worker1");
        task.createNewReplicate("worker2");
        task.createNewReplicate("worker3");

        Optional<Replicate> optional = task.getReplicate("worker4");
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    public void shouldNeedMoreReplicateNoErrorCase(){
        Task task = new Task("dappName", "cmdLine", 3);

        // basic case
        task.createNewReplicate("worker1");
        task.createNewReplicate("worker2");
        assertThat(task.needMoreReplicates()).isTrue();

        // with different statuses
        task.getReplicate("worker1").get().updateStatus(ReplicateStatus.RUNNING);
        task.getReplicate("worker2").get().updateStatus(ReplicateStatus.COMPLETED);
        assertThat(task.needMoreReplicates()).isTrue();
    }

    @Test
    public void shouldNeedMoreReplicateErrorCase(){
        Task task = new Task("dappName", "cmdLine", 3);

        task.createNewReplicate("worker1");
        task.createNewReplicate("worker2");
        task.createNewReplicate("worker3");
        task.getReplicate("worker1").get().updateStatus(ReplicateStatus.RUNNING);
        task.getReplicate("worker2").get().updateStatus(ReplicateStatus.COMPLETED);
        task.getReplicate("worker3").get().updateStatus(ReplicateStatus.ERROR);

        assertThat(task.needMoreReplicates()).isTrue();
    }

    @Test
    public void shouldNotNeedMoreReplicateNoErrorCase(){
        Task task = new Task("dappName", "cmdLine", 2);

        task.createNewReplicate("worker1");
        task.createNewReplicate("worker2");

        assertThat(task.needMoreReplicates()).isFalse();
    }

    @Test
    public void shouldNotNeedMoreReplicateErrorCase(){
        Task task = new Task("dappName", "cmdLine", 2);

        task.createNewReplicate("worker1");
        task.createNewReplicate("worker2");
        task.createNewReplicate("worker3");
        task.createNewReplicate("worker4");
        task.getReplicate("worker1").get().updateStatus(ReplicateStatus.RUNNING);
        task.getReplicate("worker2").get().updateStatus(ReplicateStatus.COMPLETED);
        task.getReplicate("worker3").get().updateStatus(ReplicateStatus.ERROR);
        task.getReplicate("worker4").get().updateStatus(ReplicateStatus.ERROR);

        assertThat(task.needMoreReplicates()).isFalse();
    }

    @Test
    public void shouldHaveWorkerAlreadyContributed(){
        Task task = new Task("dappName", "cmdLine", 4);
        task.createNewReplicate("worker1");
        task.createNewReplicate("worker2");
        task.createNewReplicate("worker3");
        task.getReplicate("worker2").get().updateStatus(ReplicateStatus.COMPLETED);
        task.getReplicate("worker3").get().updateStatus(ReplicateStatus.ERROR);

        assertThat(task.hasWorkerAlreadyContributed("worker1")).isTrue();
        assertThat(task.hasWorkerAlreadyContributed("worker2")).isTrue();
        assertThat(task.hasWorkerAlreadyContributed("worker3")).isTrue();
    }

    @Test
    public void shouldNotHaveWorkerAlreadyContributed(){
        Task task = new Task("dappName", "cmdLine", 3);
        task.createNewReplicate("worker1");
        task.createNewReplicate("worker2");

        assertThat(task.hasWorkerAlreadyContributed("newWorker")).isFalse();
    }
}
