/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.core.replicate;

import com.iexec.common.chain.ChainContribution;
import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.BytesUtils;
import com.iexec.core.chain.CredentialsService;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.result.ResultService;
import com.iexec.core.stdout.StdoutService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;
import java.util.stream.IntStream;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusModifier.*;
import static com.iexec.common.utils.TestUtils.CHAIN_TASK_ID;
import static com.iexec.common.utils.TestUtils.WALLET_WORKER_1;
import static com.iexec.common.utils.TestUtils.WALLET_WORKER_2;
import static com.iexec.common.utils.TestUtils.WALLET_WORKER_3;
import static com.iexec.common.utils.TestUtils.WALLET_WORKER_4;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReplicateServiceTests {

    private static final UpdateReplicateStatusArgs UPDATE_ARGS = UpdateReplicateStatusArgs.builder()
            .workerWeight(1)
            .build();

    @Mock
    private ReplicatesRepository replicatesRepository;
    @Mock
    private IexecHubService iexecHubService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private Web3jService web3jService;
    @Mock
    private CredentialsService credentialsService;
    @Mock
    private ResultService resultService;
    @Mock
    private StdoutService stdoutService;

    @InjectMocks
    private ReplicatesService replicatesService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldCreateNewReplicate() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(STARTING, ReplicateStatusModifier.WORKER);

        ArrayList<Replicate> list = new ArrayList<>();
        list.add(replicate1);
        list.add(replicate2);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, list);
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(replicatesRepository.save(any())).thenReturn(replicatesList);
        replicatesService.addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_3);
        Mockito.verify(replicatesRepository, Mockito.times(1))
                .save(any());
    }

    @Test
    void shouldNotCreateNewReplicate() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(STARTING, ReplicateStatusModifier.WORKER);

        ArrayList<Replicate> list = new ArrayList<>();
        list.add(replicate1);
        list.add(replicate2);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, list);
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(replicatesRepository.save(any())).thenReturn(replicatesList);

        replicatesService.addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(replicatesRepository, Mockito.times(0))
                .save(any());

        replicatesService.addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_2);
        Mockito.verify(replicatesRepository, Mockito.times(0))
                .save(any());
    }

    @Test
    void shouldCreateEmptyReplicateList() {
        replicatesService.createEmptyReplicateList(CHAIN_TASK_ID);

        Mockito.verify(replicatesRepository, Mockito.times(1)).save(new ReplicatesList(CHAIN_TASK_ID));
    }

    @Test
    void shouldGetReplicates() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate1));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        assertThat(replicatesService.getReplicates(CHAIN_TASK_ID)).isNotNull();
        assertThat(replicatesService.getReplicates(CHAIN_TASK_ID).size()).isEqualTo(1);
    }

    @Test
    void shouldNotGetReplicates() {
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        assertThat(replicatesService.getReplicates(CHAIN_TASK_ID)).isEmpty();
    }

    @Test
    void shouldGetReplicate() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(STARTING, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        assertThat(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).isEqualTo(Optional.of(replicate1));
        assertThat(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_2)).isEqualTo(Optional.of(replicate2));
    }

    @Test
    void shouldNotGetReplicate1() {
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        assertThat(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).isEqualTo(Optional.empty());
    }

    @Test
    void shouldNotGetReplicate2() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(STARTING, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        assertThat(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_3)).isEqualTo(Optional.empty());
    }

    @Test
    void shouldGetCorrectNbReplicatesWithOneStatus() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        Replicate replicate3 = new Replicate(WALLET_WORKER_3, CHAIN_TASK_ID);
        replicate3.updateStatus(STARTING, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2, replicate3));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        assertThat(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, STARTING)).isEqualTo(2);
        assertThat(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, COMPUTED)).isEqualTo(1);
        assertThat(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, CONTRIBUTED)).isEqualTo(0);
    }

    @Test
    void shouldGetCorrectNbReplicatesWithMultipleStatus() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        Replicate replicate3 = new Replicate(WALLET_WORKER_3, CHAIN_TASK_ID);
        replicate3.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        Replicate replicate4 = new Replicate(WALLET_WORKER_4, CHAIN_TASK_ID);
        replicate4.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate4.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        replicate4.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2, replicate3, replicate4));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        int shouldBe2 = replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, COMPUTED, CONTRIBUTED);
        assertThat(shouldBe2).isEqualTo(2);

        int shouldBe3 = replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, STARTING, COMPUTED);
        assertThat(shouldBe3).isEqualTo(3);

        int shouldBe4 = replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, STARTING, COMPUTED,
                CONTRIBUTED);
        assertThat(shouldBe4).isEqualTo(4);

    }

    @Test
    void shouldGetCorrectNbReplicatesWithOneLastRelevantStatus() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        Replicate replicate3 = new Replicate(WALLET_WORKER_3, CHAIN_TASK_ID);
        replicate3.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        Replicate replicate4 = new Replicate(WALLET_WORKER_4, CHAIN_TASK_ID);
        replicate4.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate4.updateStatus(WORKER_LOST, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2, replicate3, replicate4));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        assertThat(replicatesService.getNbReplicatesWithLastRelevantStatus(CHAIN_TASK_ID, STARTING)).isEqualTo(3);
        assertThat(replicatesService.getNbReplicatesWithLastRelevantStatus(CHAIN_TASK_ID, COMPUTED)).isEqualTo(1);
        assertThat(replicatesService.getNbReplicatesWithLastRelevantStatus(CHAIN_TASK_ID, CONTRIBUTED)).isEqualTo(0);
    }

    @Test
    void shouldGetCorrectNbReplicatesWithMultipleLastReleveantStatus() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        Replicate replicate3 = new Replicate(WALLET_WORKER_3, CHAIN_TASK_ID);
        replicate3.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        Replicate replicate4 = new Replicate(WALLET_WORKER_4, CHAIN_TASK_ID);
        replicate4.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate4.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        replicate4.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate5 = new Replicate(WALLET_WORKER_4, CHAIN_TASK_ID);
        replicate5.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate5.updateStatus(RECOVERING, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2, replicate3, replicate4, replicate5));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        int shouldBe2 = replicatesService.getNbReplicatesWithLastRelevantStatus(CHAIN_TASK_ID, COMPUTED, CONTRIBUTED);
        assertThat(shouldBe2).isEqualTo(2);

        int shouldBe3 = replicatesService.getNbReplicatesWithLastRelevantStatus(CHAIN_TASK_ID, STARTING, COMPUTED);
        assertThat(shouldBe3).isEqualTo(4);

        int shouldBe4 = replicatesService.getNbReplicatesWithLastRelevantStatus(CHAIN_TASK_ID, STARTING, COMPUTED,
                CONTRIBUTED);
        assertThat(shouldBe4).isEqualTo(5);

    }

    @Test
    void shouldGetCorrectNbReplicatesContainingOneStatus() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        Replicate replicate3 = new Replicate(WALLET_WORKER_3, CHAIN_TASK_ID);
        replicate3.updateStatus(STARTING, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2, replicate3));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        assertThat(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, STARTING)).isEqualTo(3);
        assertThat(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, COMPUTED)).isEqualTo(1);
        assertThat(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, CONTRIBUTED)).isEqualTo(0);
    }

    @Test
    void shouldGetCorrectNbReplicatesContainingMultipleStatus() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        Replicate replicate3 = new Replicate(WALLET_WORKER_3, CHAIN_TASK_ID);
        replicate3.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        Replicate replicate4 = new Replicate(WALLET_WORKER_4, CHAIN_TASK_ID);
        replicate4.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate4.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        replicate4.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2, replicate3, replicate4));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        int shouldBe2 = replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, COMPUTED, CONTRIBUTED);
        assertThat(shouldBe2).isEqualTo(2);

        int shouldBe4 = replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, STARTING, COMPUTED);
        assertThat(shouldBe4).isEqualTo(4);

        int shouldBe0 = replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, COMPLETED, FAILED,
                RESULT_UPLOADING);
        assertThat(shouldBe0).isEqualTo(0);
    }

    @Test
    void shouldGetReplicateWithRevealStatus() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(REVEALING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(REVEALED, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate2.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        replicate2.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate, replicate2));
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        Optional<Replicate> optional = replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID);
        assertThat(optional.isPresent()).isTrue();
        assertThat(optional).isEqualTo(Optional.of(replicate));
    }

    @Test
    void shouldNotGetReplicateWithRevealStatusSinceEmptyReplicatesList() {
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        Optional<Replicate> optional = replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID);
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    void shouldNotGetReplicateWithRevealStatusWithNonEmptyList() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(REVEALING, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate2.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        replicate2.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate, replicate2));
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        Optional<Replicate> optional = replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID);
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    void shouldUpdateReplicateStatusWithoutStdout(){
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(web3jService.isBlockAvailable(anyLong())).thenReturn(true);
        when(iexecHubService.repeatIsContributedTrue(anyString(), anyString())).thenReturn(true);
        when(replicatesRepository.save(replicatesList)).thenReturn(replicatesList);
        String resultHash = "hash";
        when(iexecHubService.getChainContribution(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(ChainContribution.builder()
                .resultHash(resultHash)
                .build()));
        when(iexecHubService.getWorkerWeight(WALLET_WORKER_1)).thenReturn(2);

        ArgumentCaptor<ReplicateUpdatedEvent> argumentCaptor = ArgumentCaptor.forClass(ReplicateUpdatedEvent.class);
        ReplicateStatusDetails details = new ReplicateStatusDetails(10L);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(CONTRIBUTED)
                .details(details)
                .build();
        replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate);

        Mockito.verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(argumentCaptor.capture());

        ReplicateUpdatedEvent capturedEvent = argumentCaptor.getAllValues().get(0);
        assertThat(capturedEvent.getChainTaskId()).isEqualTo(replicate.getChainTaskId());
        assertThat(capturedEvent.getWalletAddress()).isEqualTo(WALLET_WORKER_1);
        assertThat(capturedEvent.getReplicateStatusUpdate().getStatus()).isEqualTo(CONTRIBUTED);
        assertThat(capturedEvent.getReplicateStatusUpdate().getDetails().getChainReceipt().getBlockNumber())
                .isEqualTo(10);
        assertThat(replicatesList.getReplicates().get(0).getContributionHash()).isEqualTo(resultHash);
        Mockito.verify(stdoutService, never()).addReplicateStdout(anyString(), anyString(), anyString());
        assertThat(capturedEvent.getReplicateStatusUpdate().getDetails().getStdout()).isNull();
    }

    @Test
    void shouldUpdateReplicateStatusWithStdout(){
        String stdout = "This is an stdout message !";
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(COMPUTING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));
        ReplicateStatusDetails details = ReplicateStatusDetails.builder().stdout(stdout).build();
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(COMPUTED)
                .details(details)
                .build();
        ArgumentCaptor<ReplicateUpdatedEvent> argumentCaptor = ArgumentCaptor.forClass(ReplicateUpdatedEvent.class);
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(replicatesRepository.save(replicatesList)).thenReturn(replicatesList);

        replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate);
        Mockito.verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(argumentCaptor.capture());
        ReplicateUpdatedEvent capturedEvent = argumentCaptor.getAllValues().get(0);
        assertThat(capturedEvent.getChainTaskId()).isEqualTo(replicate.getChainTaskId());
        assertThat(capturedEvent.getWalletAddress()).isEqualTo(WALLET_WORKER_1);
        assertThat(capturedEvent.getReplicateStatusUpdate().getStatus()).isEqualTo(COMPUTED);
        Mockito.verify(stdoutService, times(1)).addReplicateStdout(CHAIN_TASK_ID, WALLET_WORKER_1, stdout);
        assertThat(capturedEvent.getReplicateStatusUpdate().getDetails().getStdout()).isNull();
    }

    @Test
    void shouldNotUpdateReplicateStatusSinceNoReplicateList(){
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, new ReplicateStatusUpdate(REVEALING));
        Mockito.verify(replicatesRepository, Mockito.times(0))
                .save(any());
        Mockito.verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
    }

    @Test
    void shouldNotUpdateReplicateStatusSinceNoMatchingReplicate(){
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        // Call on a different worker
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(REVEALING)
                .build();

        replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_2, statusUpdate);
        Mockito.verify(replicatesRepository, Mockito.times(0))
                .save(any());
        Mockito.verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
    }

    @Test
    void shouldNotUpdateReplicateStatusSinceInvalidWorkflowTransition() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(REVEALED)
                .build();

        replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate);
        Mockito.verify(replicatesRepository, Mockito.times(0))
                .save(any());
        Mockito.verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
    }

    @Test
    void shouldNotUpdateReplicateStatusSinceWrongOnChainStatus(){
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(web3jService.isBlockAvailable(anyLong())).thenReturn(true);
        when(iexecHubService.repeatIsContributedTrue(anyString(), anyString())).thenReturn(false);

        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(CONTRIBUTED)
                .build();

        replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate);
        Mockito.verify(replicatesRepository, Mockito.times(0))
                .save(any());
        Mockito.verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
    }

    @Test
    void shouldNotUpdateReplicateStatusToContributedSinceGetContributionFailed() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(web3jService.isBlockAvailable(anyLong())).thenReturn(true);
        when(iexecHubService.repeatIsContributedTrue(anyString(), anyString())).thenReturn(false);
        when(iexecHubService.getChainContribution(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.empty());
        when(replicatesRepository.save(replicatesList)).thenReturn(replicatesList);

        ReplicateStatusDetails details = new ReplicateStatusDetails(10L);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(CONTRIBUTED)
                .details(details)
                .build();

        replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate);

        Mockito.verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
    }

    @Test
    void shouldNotUpdateReplicateStatusToContributedSinceCannotGetWorkerWeight() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(web3jService.isBlockAvailable(anyLong())).thenReturn(true);
        when(iexecHubService.repeatIsContributedTrue(anyString(), anyString())).thenReturn(false);
        String resultHash = "hash";
        when(iexecHubService.getChainContribution(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(ChainContribution.builder()
                .resultHash(resultHash)
                .build()));
        when(iexecHubService.getWorkerWeight(WALLET_WORKER_1)).thenReturn(0);

        when(replicatesRepository.save(replicatesList)).thenReturn(replicatesList);

        ReplicateStatusDetails details = new ReplicateStatusDetails(10L);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(CONTRIBUTED)
                .details(details)
                .build();

        replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate);

        Mockito.verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
    }

    @Test
    void shouldNotUpdateReplicateStatusToResultUploadingSinceResultIsNotUploaded() {
        //TODO After having moved isResultUploaded() method to another class
    }

    @Test
    void shouldNotUpdateReplicateStatusToResultUploadingSinceResultLinkMissing() {
        //TODO After having moved isResultUploaded() method to another class
    }

    @Test
    void shouldNotUpdateReplicateStatusSinceAlreadyReported() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(CONTRIBUTED)
                .build();

        final Optional<TaskNotificationType> result = replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate);
        assertThat(result)
                .isEqualTo(Optional.empty());
    }

    @Test
    void shouldNotEncounterRaceConditionOnReplicateUpdate() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(REVEALING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(web3jService.isBlockAvailable(anyLong())).thenReturn(true);
        when(iexecHubService.repeatIsRevealedTrue(anyString(), anyString())).thenReturn(true);
        when(replicatesRepository.save(replicatesList)).thenReturn(replicatesList);

        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(REVEALED)
                .build();

        // Without any synchronization mechanism,
        // this would update 10 times to `REVEALED`.
        IntStream.range(0, 10)
                .parallel()
                .forEach(i -> replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate));

        assertThat(replicate.getStatusUpdateList().stream().filter(update -> REVEALED.equals(update.getStatus())).count()).isOne();
    }

    @Test
    void shouldEncounterRaceConditionOnReplicateUpdateWithoutThreadSafety() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(REVEALING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(web3jService.isBlockAvailable(anyLong())).thenReturn(true);
        when(iexecHubService.repeatIsRevealedTrue(anyString(), anyString())).thenReturn(true);
        when(replicatesRepository.save(replicatesList)).thenReturn(replicatesList);

        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(REVEALED)
                .build();

        // Without any synchronization mechanism,
        // this would update between 1 and 10 times to `REVEALED`.
        // Or this could throw a `ConcurrentModificationException`
        // on `Replicate#containsStatus` call.
        try {
            IntStream.range(0, 10)
                    .parallel()
                    .forEach(i -> replicatesService.updateReplicateStatusWithoutThreadSafety(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate, UPDATE_ARGS));
        } catch (ConcurrentModificationException e) {
            System.out.println("Concurrent modification detected," +
                    " thread safety is effectively not met.");
            return;
        }

        final long revealedUpdateCount = replicate.getStatusUpdateList()
                .stream()
                .filter(update -> REVEALED.equals(update.getStatus()))
                .count();
        assertThat(revealedUpdateCount).isPositive();

        if (revealedUpdateCount == 1) {
            System.out.println("Replicate has been updated only once" +
                    " whereas race condition should have happened");
        }
    }

    @Test
    void shouldNotSetContributionHashSinceRevealing() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(REVEALING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(web3jService.isBlockAvailable(anyLong())).thenReturn(true);
        when(iexecHubService.repeatIsRevealedTrue(anyString(), anyString())).thenReturn(true);
        when(iexecHubService.getChainContribution(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(ChainContribution.builder()
        .resultHash("hash")
        .build()));
        when(replicatesRepository.save(replicatesList)).thenReturn(replicatesList);

        ArgumentCaptor<ReplicateUpdatedEvent> argumentCaptor = ArgumentCaptor.forClass(ReplicateUpdatedEvent.class);
        ReplicateStatusDetails details = new ReplicateStatusDetails(10L);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(REVEALED)
                .details(details)
                .build();

        replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate);

        Mockito.verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(argumentCaptor.capture());

        ReplicateUpdatedEvent capturedEvent = argumentCaptor.getAllValues().get(0);
        assertThat(capturedEvent.getChainTaskId()).isEqualTo(replicate.getChainTaskId());
        assertThat(capturedEvent.getWalletAddress()).isEqualTo(WALLET_WORKER_1);
        assertThat(capturedEvent.getReplicateStatusUpdate().getStatus()).isEqualTo(REVEALED);
        assertThat(capturedEvent.getReplicateStatusUpdate().getDetails().getChainReceipt().getBlockNumber())
                .isEqualTo(10);

        assertThat(replicatesList.getReplicates().get(0).getContributionHash()).isEmpty();
    }

    @Test
    void shouldUpdateToResultUploaded() {
        String stdout = "This is an stdout message !";
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(RESULT_UPLOADING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));
        ReplicateStatusDetails details = ReplicateStatusDetails.builder().stdout(stdout).build();
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(RESULT_UPLOADED)
                .details(details)
                .build();
        ArgumentCaptor<ReplicateUpdatedEvent> argumentCaptor = ArgumentCaptor.forClass(ReplicateUpdatedEvent.class);
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(replicatesRepository.save(replicatesList)).thenReturn(replicatesList);

        final UpdateReplicateStatusArgs updateArgs = UpdateReplicateStatusArgs
                .builder()
                .chainCallbackData("callbackData")
                .taskDescription(TaskDescription.builder().callback("callback").build())
                .build();

        replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate, updateArgs);
        Mockito.verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(argumentCaptor.capture());
        ReplicateUpdatedEvent capturedEvent = argumentCaptor.getAllValues().get(0);
        assertThat(capturedEvent.getChainTaskId()).isEqualTo(replicate.getChainTaskId());
        assertThat(capturedEvent.getWalletAddress()).isEqualTo(WALLET_WORKER_1);
        assertThat(capturedEvent.getReplicateStatusUpdate().getStatus()).isEqualTo(RESULT_UPLOADED);
        assertThat(capturedEvent.getReplicateStatusUpdate().getDetails().getStdout()).isNull();
    }

    // getReplicateWithResultUploadedStatus

    @Test
    void should() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(RESULT_UPLOADED, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID,
                Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID))
                .thenReturn(Optional.of(replicatesList));

        assertThat(replicatesService
                .getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)
                .get()
                .getWalletAddress())
        .isEqualTo(WALLET_WORKER_2);
    }

    @Test
    void shouldFindReplicateContributedOnchain() {
        when(iexecHubService.repeatIsContributedTrue(any(), any()))
                .thenReturn(true);
    }

    @Test
    void shouldNotFindReplicateContributedOnchain() {
        when(iexecHubService.repeatIsContributedTrue(any(), any()))
                .thenReturn(false);
    }

    @Test
    void shouldFindReplicateRevealedOnchain() {
        when(iexecHubService.repeatIsContributedTrue(any(), any()))
                .thenReturn(true);
    }

    @Test
    void shouldNotFindReplicateRevealedOnchain() {
        when(iexecHubService.repeatIsContributedTrue(any(), any()))
                .thenReturn(true);
    }

    // isResultUploaded

    @Test
    void shouldCheckResultServiceAndReturnTrue() {
        TaskDescription taskDescription = TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .callback(BytesUtils.EMPTY_ADDRESS)
                .isTeeTask(false)
                .build();
        when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
                .thenReturn(Optional.of(taskDescription));
        when(resultService.isResultUploaded(CHAIN_TASK_ID)).thenReturn(true);

        boolean isResultUploaded = replicatesService.isResultUploaded(CHAIN_TASK_ID);
        assertThat(isResultUploaded).isTrue();
        verify(resultService).isResultUploaded(CHAIN_TASK_ID);
    }

    @Test
    void shouldCheckResultServiceAndReturnFalse() {
        TaskDescription taskDescription = TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .callback(BytesUtils.EMPTY_ADDRESS)
                .isTeeTask(false)
                .build();
        when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
                .thenReturn(Optional.of(taskDescription));
        when(resultService.isResultUploaded(CHAIN_TASK_ID)).thenReturn(false);

        boolean isResultUploaded = replicatesService.isResultUploaded(CHAIN_TASK_ID);
        assertThat(isResultUploaded).isFalse();
        verify(resultService).isResultUploaded(CHAIN_TASK_ID);
    }

    @Test
    void shouldReturnFalseSinceTaskNotFound() {
        when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());

        boolean isResultUploaded = replicatesService.isResultUploaded(CHAIN_TASK_ID);
        assertThat(isResultUploaded).isFalse();
        verify(resultService, never()).isResultUploaded(CHAIN_TASK_ID);
    }

    @Test
    void shouldReturnTrueForCallbackTask() {
        TaskDescription taskDescription = TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .callback("callback")
                .isTeeTask(false)
                .build();
        when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
                .thenReturn(Optional.of(taskDescription));

        boolean isResultUploaded = replicatesService.isResultUploaded(CHAIN_TASK_ID);
        assertThat(isResultUploaded).isTrue();
        verify(resultService, never()).isResultUploaded(CHAIN_TASK_ID);
    }

    @Test
    void shouldReturnTrueForTeeTask() {
        TaskDescription taskDescription = TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .callback(BytesUtils.EMPTY_ADDRESS)
                .isTeeTask(true)
                .build();
        when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
                .thenReturn(Optional.of(taskDescription));

        boolean isResultUploaded = replicatesService.isResultUploaded(CHAIN_TASK_ID);
        assertThat(isResultUploaded).isTrue();
        verify(resultService, never()).isResultUploaded(CHAIN_TASK_ID);
    }

    // didReplicateContributeOnchain

    @Test
    void shouldReturnFindReplicateContributedOnchain() {
        when(
                iexecHubService.isStatusTrueOnChain(
                        CHAIN_TASK_ID,
                        WALLET_WORKER_1,
                        ChainContributionStatus.CONTRIBUTED
                )
        ).thenReturn(true);
        assertThat(
                replicatesService.didReplicateContributeOnchain(
                        CHAIN_TASK_ID,
                        WALLET_WORKER_1
                )
        ).isTrue();
    }

    // didReplicateRevealOnchain

    @Test
    void shouldFindReplicatedReveledOnchain() {
        when(
                iexecHubService.isStatusTrueOnChain(
                        CHAIN_TASK_ID,
                        WALLET_WORKER_1,
                        ChainContributionStatus.REVEALED
                )
        ).thenReturn(true);
        assertThat(
                replicatesService.didReplicateRevealOnchain(
                        CHAIN_TASK_ID,
                        WALLET_WORKER_1
                )
        ).isTrue();
    }

    // setRevealTimeoutStatusIfNeeded

    @Test
    void shouldSetTriggerReplicateUpdateIfRevealTimeout() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);

        replicatesService.setRevealTimeoutStatusIfNeeded(CHAIN_TASK_ID, replicate);

        verify(replicatesRepository).findByChainTaskId(CHAIN_TASK_ID);
    }

    // canUpdateReplicateStatus

    @Test
    void shouldAuthorizeUpdate() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(REVEALING)
                .build();

        assertThat(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate, UPDATE_ARGS))
                .isEqualTo(ReplicateStatusUpdateError.NO_ERROR);
    }

    @Test
    void shouldNotAuthorizeUpdateSinceNoMatchingReplicate() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(REVEALING)
                .build();

        assertThat(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_2, statusUpdate, UPDATE_ARGS))
                .isEqualTo(ReplicateStatusUpdateError.UNKNOWN_REPLICATE);
    }

    @Test
    void shouldNotAuthorizeUpdateSinceAlreadyReported() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(CONTRIBUTED)
                .build();

        assertThat(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate, UPDATE_ARGS))
                .isEqualTo(ReplicateStatusUpdateError.ALREADY_REPORTED);
    }

    @Test
    void shouldNotAuthorizeUpdateSinceBadWorkflowTransition() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(REVEALING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(CONTRIBUTED)
                .build();

        assertThat(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate, UPDATE_ARGS))
                .isEqualTo(ReplicateStatusUpdateError.BAD_WORKFLOW_TRANSITION);
    }

    @Test
    void shouldNotAuthorizeUpdateSinceContributeFailed() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(CONTRIBUTE_FAILED)
                .build();

        assertThat(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate, UPDATE_ARGS))
                .isEqualTo(ReplicateStatusUpdateError.GENERIC_CANT_UPDATE);
    }

    @Test
    void shouldNotAuthorizeUpdateSinceRevealFailed() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(REVEALING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(REVEAL_FAILED)
                .build();

        assertThat(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate, UPDATE_ARGS))
                .isEqualTo(ReplicateStatusUpdateError.GENERIC_CANT_UPDATE);
    }

    @Test
    void shouldAuthorizeUpdateOnResultUploadFailed() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(RESULT_UPLOADING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(RESULT_UPLOAD_FAILED)
                .build();
        UpdateReplicateStatusArgs updateReplicateStatusArgs = UpdateReplicateStatusArgs
                .builder()
                .taskDescription(new TaskDescription())
                .build();

        assertThat(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate, updateReplicateStatusArgs))
                .isEqualTo(ReplicateStatusUpdateError.NO_ERROR);
    }

    @Test
    void shouldNotAuthorizeUpdateOnResultUploadFailedSinceResultUploadedWithCallback() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(RESULT_UPLOADING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(RESULT_UPLOAD_FAILED)
                .build();
        UpdateReplicateStatusArgs updateReplicateStatusArgs = UpdateReplicateStatusArgs
                .builder()
                .taskDescription(TaskDescription.builder().callback("callback").build())
                .build();

        assertThat(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate, updateReplicateStatusArgs))
                .isEqualTo(ReplicateStatusUpdateError.GENERIC_CANT_UPDATE);
    }

    @Test
    void shouldNotAuthorizeUpdateOnResultUploadFailedSinceResultUploadedWithTee() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(RESULT_UPLOADING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(RESULT_UPLOAD_FAILED)
                .build();
        UpdateReplicateStatusArgs updateReplicateStatusArgs = UpdateReplicateStatusArgs
                .builder()
                .taskDescription(TaskDescription.builder().isTeeTask(true).build())
                .build();

        assertThat(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate, updateReplicateStatusArgs))
                .isEqualTo(ReplicateStatusUpdateError.GENERIC_CANT_UPDATE);
    }

    @Test
    void shouldNotAuthorizeUpdateOnContributedSinceNoBlockAvailable() {
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTING, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(CONTRIBUTED)
                .build();

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(web3jService.isBlockAvailable(anyLong())).thenReturn(false);

        assertThat(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate, UPDATE_ARGS))
                .isEqualTo(ReplicateStatusUpdateError.GENERIC_CANT_UPDATE);
    }

    @Test
    void shouldNotAuthorizeUpdateOnContributedSinceWorkerWeightNotValid() {
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTING, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(CONTRIBUTED)
                .build();
        final UpdateReplicateStatusArgs updateArgs = UpdateReplicateStatusArgs
                .builder()
                .workerWeight(0)
                .build();

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(web3jService.isBlockAvailable(anyLong())).thenReturn(true);
        when(iexecHubService.repeatIsContributedTrue(anyString(), anyString())).thenReturn(true);

        assertThat(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate, updateArgs))
                .isEqualTo(ReplicateStatusUpdateError.GENERIC_CANT_UPDATE);
    }

    @Test
    void shouldNotAuthorizeUpdateOnContributedSinceNoChainContribution() {
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTING, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(CONTRIBUTED)
                .build();
        final UpdateReplicateStatusArgs updateArgs = UpdateReplicateStatusArgs
                .builder()
                .workerWeight(1)
                .chainContribution(null)
                .build();

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(web3jService.isBlockAvailable(anyLong())).thenReturn(true);
        when(iexecHubService.repeatIsContributedTrue(anyString(), anyString())).thenReturn(true);

        assertThat(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate, updateArgs))
                .isEqualTo(ReplicateStatusUpdateError.GENERIC_CANT_UPDATE);
    }

    @Test
    void shouldNotAuthorizeUpdateOnContributedSinceNoChainContributionResultHash() {
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTING, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(CONTRIBUTED)
                .build();
        final UpdateReplicateStatusArgs updateArgs = UpdateReplicateStatusArgs
                .builder()
                .workerWeight(1)
                .chainContribution(ChainContribution.builder().resultHash("").build())
                .build();

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(web3jService.isBlockAvailable(anyLong())).thenReturn(true);
        when(iexecHubService.repeatIsContributedTrue(anyString(), anyString())).thenReturn(true);

        assertThat(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate, updateArgs))
                .isEqualTo(ReplicateStatusUpdateError.GENERIC_CANT_UPDATE);
    }

    @Test
    void shouldAuthorizeUpdateOnResultUploaded() {
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(RESULT_UPLOADING, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(RESULT_UPLOADED)
                .build();
        final UpdateReplicateStatusArgs updateArgs = UpdateReplicateStatusArgs
                .builder()
                .chainCallbackData("callbackData")
                .taskDescription(TaskDescription.builder().callback("callback").build())
                .build();

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(web3jService.isBlockAvailable(anyLong())).thenReturn(true);
        when(iexecHubService.repeatIsContributedTrue(anyString(), anyString())).thenReturn(true);

        assertThat(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate, updateArgs))
                .isEqualTo(ReplicateStatusUpdateError.NO_ERROR);
    }

    @Test
    void shouldNotAuthorizeUpdateOnResultUploadedSinceNoChainCallbackData() {
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(RESULT_UPLOADING, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(RESULT_UPLOADED)
                .build();
        final UpdateReplicateStatusArgs updateArgs = UpdateReplicateStatusArgs
                .builder()
                .taskDescription(TaskDescription.builder().callback("callback").build())
                .build();

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        assertThat(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate, updateArgs))
                .isEqualTo(ReplicateStatusUpdateError.GENERIC_CANT_UPDATE);
    }

    @Test
    void shouldNotAuthorizeUpdateOnResultUploadedSinceNoResultLink() {
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(RESULT_UPLOADING, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(RESULT_UPLOADED)
                .build();
        final UpdateReplicateStatusArgs updateArgs = UpdateReplicateStatusArgs
                .builder()
                .taskDescription(TaskDescription.builder().chainTaskId(CHAIN_TASK_ID).build())
                .build();

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(resultService.isResultUploaded(CHAIN_TASK_ID)).thenReturn(true);

        assertThat(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate, updateArgs))
                .isEqualTo(ReplicateStatusUpdateError.GENERIC_CANT_UPDATE);
    }

    @Test
    void shouldNotAuthorizeUpdateOnResultUploadedSinceResultNotUploaded() {
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(RESULT_UPLOADING, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(RESULT_UPLOADED)
                .build();
        final UpdateReplicateStatusArgs updateArgs = UpdateReplicateStatusArgs
                .builder()
                .taskDescription(TaskDescription.builder().build())
                .build();

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(resultService.isResultUploaded(anyString())).thenReturn(false);

        assertThat(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate, updateArgs))
                .isEqualTo(ReplicateStatusUpdateError.GENERIC_CANT_UPDATE);
    }

    // computeUpdateReplicateStatusArgs

    @Test
    void computeUpdateReplicateStatusArgsContributed() {
        final int expectedWorkerWeight = 1;
        final ChainContribution expectedChainContribution = new ChainContribution();
        final String unexpectedResultLink = "resultLink";
        final String unexpectedChainCallbackData = "chainCallbackData";

        final ReplicateStatusDetails details = ReplicateStatusDetails.builder()
                .resultLink(unexpectedResultLink)
                .chainCallbackData(unexpectedChainCallbackData)
                .build();
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(CONTRIBUTED)
                .details(details)
                .build();

        when(iexecHubService.getWorkerWeight(WALLET_WORKER_1)).thenReturn(expectedWorkerWeight);
        when(iexecHubService.getChainContribution(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(Optional.of(new ChainContribution()));

        assertThat(replicatesService.computeUpdateReplicateStatusArgs(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate))
                .isEqualTo(UpdateReplicateStatusArgs.builder()
                        .workerWeight(expectedWorkerWeight)
                        .chainContribution(expectedChainContribution)
                        .build());
    }

    @Test
    void computeUpdateReplicateStatusArgsResultUploaded() {
        final int unexpectedWorkerWeight = 1;
        final ChainContribution unexpectedChainContribution = new ChainContribution();
        final String expectedResultLink = "resultLink";
        final String expectedChainCallbackData = "chainCallbackData";
        final TaskDescription expectedTaskDescription = TaskDescription.builder().build();

        final ReplicateStatusDetails details = ReplicateStatusDetails.builder()
                .resultLink(expectedResultLink)
                .chainCallbackData(expectedChainCallbackData)
                .build();
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(RESULT_UPLOADED)
                .details(details)
                .build();

        when(iexecHubService.getWorkerWeight(WALLET_WORKER_1)).thenReturn(unexpectedWorkerWeight);
        when(iexecHubService.getChainContribution(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(Optional.of(unexpectedChainContribution));
        when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
                .thenReturn(Optional.of(expectedTaskDescription));

        final UpdateReplicateStatusArgs actualResult =
                replicatesService.computeUpdateReplicateStatusArgs(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate);
        assertThat(actualResult)
                .isEqualTo(UpdateReplicateStatusArgs.builder()
                        .resultLink(expectedResultLink)
                        .chainCallbackData(expectedChainCallbackData)
                        .taskDescription(expectedTaskDescription)
                        .build());
    }

    @Test
    void computeUpdateReplicateStatusArgsResultUploadFailed() {
        final int unexpectedWorkerWeight = 1;
        final ChainContribution unexpectedChainContribution = new ChainContribution();
        final String unexpectedResultLink = "resultLink";
        final String unexpectedChainCallbackData = "chainCallbackData";
        final TaskDescription expectedTaskDescription = TaskDescription.builder().build();

        final ReplicateStatusDetails details = ReplicateStatusDetails.builder()
                .resultLink(unexpectedResultLink)
                .chainCallbackData(unexpectedChainCallbackData)
                .build();
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(RESULT_UPLOAD_FAILED)
                .details(details)
                .build();

        when(iexecHubService.getWorkerWeight(WALLET_WORKER_1)).thenReturn(unexpectedWorkerWeight);
        when(iexecHubService.getChainContribution(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(Optional.of(unexpectedChainContribution));
        when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
                .thenReturn(Optional.of(expectedTaskDescription));

        final UpdateReplicateStatusArgs actualResult =
                replicatesService.computeUpdateReplicateStatusArgs(CHAIN_TASK_ID, WALLET_WORKER_1, statusUpdate);
        assertThat(actualResult)
                .isEqualTo(UpdateReplicateStatusArgs.builder()
                        .taskDescription(expectedTaskDescription)
                        .build());
    }

}