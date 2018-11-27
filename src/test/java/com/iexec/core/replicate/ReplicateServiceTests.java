package com.iexec.core.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class ReplicateServiceTests {

    private final static String WALLET_WORKER_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private final static String WALLET_WORKER_2 = "0x2ab2674aa374fe6415d11f0a8fcbd8027fc1e6a9";
    private final static String WALLET_WORKER_3 = "0x3a3406e69adf886c442ff1791cbf67cea679275d";
    private final static String WALLET_WORKER_4 = "0x4aef50214110fdad4e8b9128347f2ba1ec72f614";

    private final static String CHAIN_TASK_ID = "CHAIN_TASK_ID";

    @Mock
    private ReplicatesRepository replicatesRepository;

    @InjectMocks
    private ReplicatesService replicatesService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldCreateNewReplicate(){
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING);
        replicate1.updateStatus(ReplicateStatus.COMPUTED);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING);

        ArrayList<Replicate> list = new ArrayList<>();
        list.add(replicate1);
        list.add(replicate2);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, list);
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(replicatesRepository.save(any())).thenReturn(replicatesList);
        replicatesService.createNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_3);
        Mockito.verify(replicatesRepository, Mockito.times(1))
                .save(any());
    }

    @Test
    public void shouldNotCreateNewReplicate(){
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING);
        replicate1.updateStatus(ReplicateStatus.COMPUTED);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING);

        ArrayList<Replicate> list = new ArrayList<>();
        list.add(replicate1);
        list.add(replicate2);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, list);
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(replicatesRepository.save(any())).thenReturn(replicatesList);

        replicatesService.createNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(replicatesRepository, Mockito.times(0))
                .save(any());

        replicatesService.createNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_2);
        Mockito.verify(replicatesRepository, Mockito.times(0))
                .save(any());
    }

    @Test
    public void shouldGetReplicates(){
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING);
        replicate1.updateStatus(ReplicateStatus.COMPUTED);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate1));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        assertThat(replicatesService.getReplicates(CHAIN_TASK_ID)).isNotNull();
        assertThat(replicatesService.getReplicates(CHAIN_TASK_ID).size()).isEqualTo(1);
    }

    @Test
    public void shouldNotGetReplicates(){
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        assertThat(replicatesService.getReplicates(CHAIN_TASK_ID)).isEmpty();
    }

    @Test
    public void shouldGetReplicate(){
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING);
        replicate1.updateStatus(ReplicateStatus.COMPUTED);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        assertThat(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).isEqualTo(Optional.of(replicate1));
        assertThat(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_2)).isEqualTo(Optional.of(replicate2));
    }

    @Test
    public void shouldNotGetReplicate1(){
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        assertThat(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).isEqualTo(Optional.empty());
    }

    @Test
    public void shouldNotGetReplicate2(){
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING);
        replicate1.updateStatus(ReplicateStatus.COMPUTED);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        assertThat(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_3)).isEqualTo(Optional.empty());
    }


    @Test
    public void shouldHaveWorkerAlreadyContributed(){
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING);
        replicate1.updateStatus(ReplicateStatus.COMPUTED);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        assertThat(replicatesService.hasWorkerAlreadyContributed(CHAIN_TASK_ID, WALLET_WORKER_1)).isTrue();
        assertThat(replicatesService.hasWorkerAlreadyContributed(CHAIN_TASK_ID, WALLET_WORKER_2)).isTrue();
    }

    @Test
    public void shouldNotHaveWorkerAlreadyContributed(){
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING);
        replicate1.updateStatus(ReplicateStatus.COMPUTED);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        assertThat(replicatesService.hasWorkerAlreadyContributed(CHAIN_TASK_ID, WALLET_WORKER_3)).isFalse();
    }

    @Test
    public void shouldGetCorrectNbReplicatesWithOneStatus(){
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING);
        replicate1.updateStatus(ReplicateStatus.COMPUTED);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING);
        Replicate replicate3 = new Replicate(WALLET_WORKER_3, CHAIN_TASK_ID);
        replicate3.updateStatus(ReplicateStatus.RUNNING);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2, replicate3));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        assertThat(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING)).isEqualTo(2);
        assertThat(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).isEqualTo(1);
        assertThat(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.CONTRIBUTED)).isEqualTo(0);
    }

    @Test
    public void shouldGetCorrectNbReplicatesWithMultipleStatus(){
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING);
        replicate1.updateStatus(ReplicateStatus.COMPUTED);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING);
        Replicate replicate3 = new Replicate(WALLET_WORKER_3, CHAIN_TASK_ID);
        replicate3.updateStatus(ReplicateStatus.RUNNING);
        Replicate replicate4 = new Replicate(WALLET_WORKER_4, CHAIN_TASK_ID);
        replicate4.updateStatus(ReplicateStatus.RUNNING);
        replicate4.updateStatus(ReplicateStatus.COMPUTED);
        replicate4.updateStatus(ReplicateStatus.CONTRIBUTED);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2, replicate3, replicate4));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        int shouldBe2 = replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED, ReplicateStatus.CONTRIBUTED);
        assertThat(shouldBe2).isEqualTo(2);

        int shouldBe3 = replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED);
        assertThat(shouldBe3).isEqualTo(3);

        int shouldBe4 = replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED,
                ReplicateStatus.CONTRIBUTED);
        assertThat(shouldBe4).isEqualTo(4);

    }
}

