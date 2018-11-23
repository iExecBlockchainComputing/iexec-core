package com.iexec.core.task;


import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.replicate.Replicate;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.iexec.core.utils.DateTimeUtils.addMinutesToDate;
import static org.assertj.core.api.Assertions.assertThat;

public class TaskTests {

    private final static String WALLET_ADDRESS_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private final static String WALLET_ADDRESS_2 = "0x2ab2674aa374fe6415d11f0a8fcbd8027fc1e6a9";
    private final static String WALLET_ADDRESS_3 = "0x3a3406e69adf886c442ff1791cbf67cea679275d";
    private final static String WALLET_ADDRESS_4 = "0x4aef50214110fdad4e8b9128347f2ba1ec72f614";

    @Test
    public void shouldInitializeProperly(){
        Task task = new Task("dappName", "cmdLine", 2);

        assertThat(task.getDateStatusList().size()).isEqualTo(1);
        assertThat(task.getDateStatusList().get(0).getStatus()).isEqualTo(TaskStatus.CREATED);
    }

    @Test
    public void shouldSetCurrentStatus() {
        Task task = new Task("dappName", "cmdLine", 2);
        assertThat(task.getDateStatusList().size()).isEqualTo(1);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.CREATED);

        task.changeStatus(TaskStatus.RUNNING);
        assertThat(task.getDateStatusList().size()).isEqualTo(2);
        assertThat(task.getDateStatusList().get(0).getStatus()).isEqualTo(TaskStatus.CREATED);
        assertThat(task.getDateStatusList().get(1).getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RUNNING);

        task.changeStatus(TaskStatus.COMPUTED);
        assertThat(task.getDateStatusList().size()).isEqualTo(3);
        assertThat(task.getDateStatusList().get(2).getStatus()).isEqualTo(TaskStatus.COMPUTED);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.COMPUTED);
    }

    @Test
    public void shouldGetCorrectLastStatusChange(){
        Task task = new Task("dappName", "cmdLine", 2);
        Date oneMinuteAgo = addMinutesToDate(new Date(), -1);

        TaskStatusChange latestChange = task.getLatestStatusChange();
        assertThat(latestChange.getStatus()).isEqualTo(TaskStatus.CREATED);

        task.changeStatus(TaskStatus.RUNNING);
        latestChange = task.getLatestStatusChange();
        assertThat(latestChange.getDate().after(oneMinuteAgo)).isTrue();
        assertThat(latestChange.getStatus()).isEqualTo(TaskStatus.RUNNING);

        task.changeStatus(TaskStatus.COMPUTED);
        latestChange = task.getLatestStatusChange();
        assertThat(latestChange.getDate().after(oneMinuteAgo)).isTrue();
        assertThat(latestChange.getStatus()).isEqualTo(TaskStatus.COMPUTED);
    }

    @Test
    public void shouldCreateNewReplicate(){
        String chainTaskId = "chainTaskId";
        Task task = new Task("dappName", "cmdLine", 2, chainTaskId);
        assertThat(task.getReplicates()).isEmpty();

        task.createNewReplicate(WALLET_ADDRESS_1);
        assertThat(task.getReplicates().size()).isEqualTo(1);
        assertThat(task.getReplicates().get(0).getWalletAddress()).isEqualTo(WALLET_ADDRESS_1);
        assertThat(task.getReplicates().get(0).getChainTaskId()).isEqualTo(chainTaskId);

        task.createNewReplicate(WALLET_ADDRESS_2);
        assertThat(task.getReplicates().size()).isEqualTo(2);
        assertThat(task.getReplicates().get(1).getWalletAddress()).isEqualTo(WALLET_ADDRESS_2);
        assertThat(task.getReplicates().get(0).getChainTaskId()).isEqualTo(chainTaskId);
    }

    @Test
    public void shouldGetExistingReplicate(){
        Task task = new Task("dappName", "cmdLine", 2);
        task.createNewReplicate(WALLET_ADDRESS_1);
        task.createNewReplicate(WALLET_ADDRESS_2);
        task.createNewReplicate(WALLET_ADDRESS_3);

        Optional<Replicate> optional = task.getReplicate(WALLET_ADDRESS_2);
        assertThat(optional.isPresent()).isTrue();
        assertThat(optional.get().getWalletAddress()).isEqualTo(WALLET_ADDRESS_2);
    }

    @Test
    public void shouldNotGetAnyReplicate(){
        Task task = new Task("dappName", "cmdLine", 2);
        task.createNewReplicate(WALLET_ADDRESS_1);
        task.createNewReplicate(WALLET_ADDRESS_2);
        task.createNewReplicate(WALLET_ADDRESS_3);

        Optional<Replicate> optional = task.getReplicate("0xdummyWallet");
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    public void shouldNeedMoreReplicateBasicCase(){
        Task task = new Task("dappName", "cmdLine", 3);

        // basic case
        task.createNewReplicate(WALLET_ADDRESS_1);
        task.createNewReplicate(WALLET_ADDRESS_2);
        assertThat(task.needMoreReplicates()).isTrue();

        // with different statuses
        task.getReplicate(WALLET_ADDRESS_1).get().updateStatus(ReplicateStatus.RUNNING);
        task.getReplicate(WALLET_ADDRESS_2).get().updateStatus(ReplicateStatus.COMPUTED);
        assertThat(task.needMoreReplicates()).isTrue();
    }

    @Test
    public void shouldNeedMoreReplicateErrorCase(){
        Task task = new Task("dappName", "cmdLine", 3);

        task.createNewReplicate(WALLET_ADDRESS_1);
        task.createNewReplicate(WALLET_ADDRESS_2);
        task.createNewReplicate(WALLET_ADDRESS_3);
        task.getReplicate(WALLET_ADDRESS_1).get().updateStatus(ReplicateStatus.RUNNING);
        task.getReplicate(WALLET_ADDRESS_2).get().updateStatus(ReplicateStatus.COMPUTED);
        task.getReplicate(WALLET_ADDRESS_3).get().updateStatus(ReplicateStatus.ERROR);

        assertThat(task.needMoreReplicates()).isTrue();
    }

    @Test
    public void shouldNeedMoreReplicateWorkerLostCase(){
        Task task = new Task("dappName", "cmdLine", 3);

        task.createNewReplicate(WALLET_ADDRESS_1);
        task.createNewReplicate(WALLET_ADDRESS_2);
        task.createNewReplicate(WALLET_ADDRESS_3);
        task.createNewReplicate(WALLET_ADDRESS_4);
        task.getReplicate(WALLET_ADDRESS_1).get().updateStatus(ReplicateStatus.RUNNING);
        task.getReplicate(WALLET_ADDRESS_2).get().updateStatus(ReplicateStatus.COMPUTED);
        task.getReplicate(WALLET_ADDRESS_3).get().updateStatus(ReplicateStatus.ERROR);
        task.getReplicate(WALLET_ADDRESS_4).get().updateStatus(ReplicateStatus.WORKER_LOST);

        assertThat(task.needMoreReplicates()).isTrue();
    }

    @Test
    public void shouldNotNeedMoreReplicateNoErrorCase(){
        Task task = new Task("dappName", "cmdLine", 2);

        task.createNewReplicate(WALLET_ADDRESS_1);
        task.createNewReplicate(WALLET_ADDRESS_2);
        task.createNewReplicate(WALLET_ADDRESS_3);

        assertThat(task.needMoreReplicates()).isFalse();
    }

    @Test
    public void shouldNotNeedMoreReplicateErrorCase(){
        Task task = new Task("dappName", "cmdLine", 2);

        task.createNewReplicate(WALLET_ADDRESS_1);
        task.createNewReplicate(WALLET_ADDRESS_2);
        task.createNewReplicate(WALLET_ADDRESS_3);
        task.createNewReplicate(WALLET_ADDRESS_4);
        task.getReplicate(WALLET_ADDRESS_1).get().updateStatus(ReplicateStatus.RUNNING);
        task.getReplicate(WALLET_ADDRESS_2).get().updateStatus(ReplicateStatus.COMPUTED);
        task.getReplicate(WALLET_ADDRESS_3).get().updateStatus(ReplicateStatus.ERROR);
        task.getReplicate(WALLET_ADDRESS_4).get().updateStatus(ReplicateStatus.ERROR);

        assertThat(task.needMoreReplicates()).isFalse();
    }

    @Test
    public void shouldHaveWorkerAlreadyContributed(){
        Task task = new Task("dappName", "cmdLine", 4);
        task.createNewReplicate(WALLET_ADDRESS_1);
        task.createNewReplicate(WALLET_ADDRESS_2);
        task.createNewReplicate(WALLET_ADDRESS_3);
        task.getReplicate(WALLET_ADDRESS_2).get().updateStatus(ReplicateStatus.COMPUTED);
        task.getReplicate(WALLET_ADDRESS_3).get().updateStatus(ReplicateStatus.ERROR);

        assertThat(task.hasWorkerAlreadyContributed(WALLET_ADDRESS_1)).isTrue();
        assertThat(task.hasWorkerAlreadyContributed(WALLET_ADDRESS_2)).isTrue();
        assertThat(task.hasWorkerAlreadyContributed(WALLET_ADDRESS_3)).isTrue();
    }

    @Test
    public void shouldNotHaveWorkerAlreadyContributed(){
        Task task = new Task("dappName", "cmdLine", 3);
        task.createNewReplicate(WALLET_ADDRESS_1);
        task.createNewReplicate(WALLET_ADDRESS_2);

        assertThat(task.hasWorkerAlreadyContributed("newWorker")).isFalse();
    }

    @Test
    public void shouldCountNbReplicateStatusCorrectly(){
        Task task = new Task("dappName", "cmdLine", 3);
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate(WALLET_ADDRESS_1, "taskId"));
        replicates.add(new Replicate(WALLET_ADDRESS_2, "taskId"));
        replicates.add(new Replicate(WALLET_ADDRESS_3, "taskId"));
        replicates.get(0).updateStatus(ReplicateStatus.RUNNING);
        replicates.get(1).updateStatus(ReplicateStatus.COMPUTED);
        replicates.get(2).updateStatus(ReplicateStatus.COMPUTED);
        task.setReplicates(replicates);

        assertThat(task.getNbReplicatesWithStatus(ReplicateStatus.RUNNING)).isEqualTo(1);
        assertThat(task.getNbReplicatesWithStatus(ReplicateStatus.COMPUTED)).isEqualTo(2);
    }

    @Test
    public void shouldCountNbReplicateStatusCorrectlyOrCase(){
        Task task = new Task("dappName", "cmdLine", 4);
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate(WALLET_ADDRESS_1, "taskId"));
        replicates.add(new Replicate(WALLET_ADDRESS_2, "taskId"));
        replicates.add(new Replicate(WALLET_ADDRESS_3, "taskId"));
        replicates.add(new Replicate(WALLET_ADDRESS_4, "taskId"));
        replicates.get(0).updateStatus(ReplicateStatus.RUNNING);
        replicates.get(1).updateStatus(ReplicateStatus.COMPUTED);
        replicates.get(2).updateStatus(ReplicateStatus.COMPUTED);
        task.setReplicates(replicates);

        assertThat(task.getNbReplicatesStatusEqualTo(ReplicateStatus.CREATED)).isEqualTo(1);
        assertThat(task.getNbReplicatesStatusEqualTo(ReplicateStatus.CREATED, ReplicateStatus.RUNNING)).isEqualTo(2);
        assertThat(task.getNbReplicatesStatusEqualTo(ReplicateStatus.COMPUTED)).isEqualTo(2);
        assertThat(task.getNbReplicatesStatusEqualTo(ReplicateStatus.COMPUTED, ReplicateStatus.RUNNING)).isEqualTo(3);
        assertThat(task.getNbReplicatesStatusEqualTo(ReplicateStatus.CREATED, ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED)).isEqualTo(4);
    }
}
