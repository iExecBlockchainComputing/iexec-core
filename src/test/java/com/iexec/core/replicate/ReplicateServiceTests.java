package com.iexec.core.replicate;

import com.iexec.common.chain.ChainContribution;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.core.chain.CredentialsService;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.result.ResultRepoService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.*;
import org.springframework.context.ApplicationEventPublisher;
import org.web3j.crypto.Credentials;

import java.util.*;

import static com.iexec.common.replicate.ReplicateStatus.CONTRIBUTED;
import static com.iexec.common.replicate.ReplicateStatus.REVEALED;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ReplicateServiceTests {

    private final static String WALLET_WORKER_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private final static String WALLET_WORKER_2 = "0x2ab2674aa374fe6415d11f0a8fcbd8027fc1e6a9";
    private final static String WALLET_WORKER_3 = "0x3a3406e69adf886c442ff1791cbf67cea679275d";
    private final static String WALLET_WORKER_4 = "0x4aef50214110fdad4e8b9128347f2ba1ec72f614";

    private final static String CHAIN_TASK_ID = "chainTaskId";

    @Mock private ReplicatesRepository replicatesRepository;
    @Mock private IexecHubService iexecHubService;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private Web3jService web3jService;
    @Mock private CredentialsService credentialsService;
    @Mock private ResultRepoService resultRepoService;

    @InjectMocks
    private ReplicatesService replicatesService;


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldCreateNewReplicate() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);

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
    public void shouldNotCreateNewReplicate() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);

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
    public void shouldCreateEmptyReplicateList() {
        replicatesService.createEmptyReplicateList(CHAIN_TASK_ID);

        Mockito.verify(replicatesRepository, Mockito.times(1)).save(new ReplicatesList(CHAIN_TASK_ID));
    }

    @Test
    public void shouldGetReplicates() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate1));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        assertThat(replicatesService.getReplicates(CHAIN_TASK_ID)).isNotNull();
        assertThat(replicatesService.getReplicates(CHAIN_TASK_ID).size()).isEqualTo(1);
    }

    @Test
    public void shouldNotGetReplicates() {
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        assertThat(replicatesService.getReplicates(CHAIN_TASK_ID)).isEmpty();
    }

    @Test
    public void shouldGetReplicate() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        assertThat(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).isEqualTo(Optional.of(replicate1));
        assertThat(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_2)).isEqualTo(Optional.of(replicate2));
    }

    @Test
    public void shouldNotGetReplicate1() {
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        assertThat(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).isEqualTo(Optional.empty());
    }

    @Test
    public void shouldNotGetReplicate2() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        assertThat(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_3)).isEqualTo(Optional.empty());
    }

    @Test
    public void shouldHaveWorkerAlreadyContributed() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        assertThat(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1)).isTrue();
        assertThat(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_2)).isTrue();
    }

    @Test
    public void shouldNotHaveWorkerAlreadyContributed() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        assertThat(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_3)).isFalse();
    }

    @Test
    public void shouldGetCorrectNbReplicatesWithOneStatus() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        Replicate replicate3 = new Replicate(WALLET_WORKER_3, CHAIN_TASK_ID);
        replicate3.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2, replicate3));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        assertThat(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING)).isEqualTo(2);
        assertThat(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).isEqualTo(1);
        assertThat(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.CONTRIBUTED)).isEqualTo(0);
    }

    @Test
    public void shouldGetCorrectNbReplicatesWithMultipleStatus() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        Replicate replicate3 = new Replicate(WALLET_WORKER_3, CHAIN_TASK_ID);
        replicate3.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        Replicate replicate4 = new Replicate(WALLET_WORKER_4, CHAIN_TASK_ID);
        replicate4.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate4.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        replicate4.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2, replicate3, replicate4));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        int shouldBe2 = replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED, ReplicateStatus.CONTRIBUTED);
        assertThat(shouldBe2).isEqualTo(2);

        int shouldBe3 = replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED);
        assertThat(shouldBe3).isEqualTo(3);

        int shouldBe4 = replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED,
                ReplicateStatus.CONTRIBUTED);
        assertThat(shouldBe4).isEqualTo(4);

    }

    @Test
    public void shouldGetCorrectNbReplicatesContainingOneStatus() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        Replicate replicate3 = new Replicate(WALLET_WORKER_3, CHAIN_TASK_ID);
        replicate3.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2, replicate3));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        assertThat(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING)).isEqualTo(3);
        assertThat(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).isEqualTo(1);
        assertThat(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.CONTRIBUTED)).isEqualTo(0);
    }

    @Test
    public void shouldGetCorrectNbReplicatesContainingMultipleStatus() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        Replicate replicate3 = new Replicate(WALLET_WORKER_3, CHAIN_TASK_ID);
        replicate3.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        Replicate replicate4 = new Replicate(WALLET_WORKER_4, CHAIN_TASK_ID);
        replicate4.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate4.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        replicate4.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2, replicate3, replicate4));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        int shouldBe2 = replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED, ReplicateStatus.CONTRIBUTED);
        assertThat(shouldBe2).isEqualTo(2);

        int shouldBe4 = replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED);
        assertThat(shouldBe4).isEqualTo(4);

        int shouldBe0 = replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.COMPLETED, ReplicateStatus.FAILED,
                ReplicateStatus.RESULT_UPLOADING);
        assertThat(shouldBe0).isEqualTo(0);
    }

    @Test
    public void shouldGetReplicateWithRevealStatus() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(REVEALED, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate2.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        replicate2.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate, replicate2));
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        Optional<Replicate> optional = replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID);
        assertThat(optional.isPresent()).isTrue();
        assertThat(optional).isEqualTo(Optional.of(replicate));
    }

    @Test
    public void shouldNotGetReplicateWithRevealStatusSinceEmptyReplicatesList() {
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        Optional<Replicate> optional = replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID);
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    public void shouldNotGetReplicateWithRevealStatusWithNonEmptyList() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate2.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        replicate2.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate, replicate2));
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        Optional<Replicate> optional = replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID);
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    public void shouldNeedMoreReplicates(){
        final long maxExecutionTime = 60000;
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.WORKER_LOST, ReplicateStatusModifier.POOL_MANAGER);
        Replicate replicate3 = new Replicate(WALLET_WORKER_3, CHAIN_TASK_ID);
        replicate3.updateStatus(ReplicateStatus.CANT_CONTRIBUTE_SINCE_STAKE_TOO_LOW, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate, replicate2, replicate3));
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        // numWorkersNeeded strictly bigger than the number of running replicates
        boolean res = replicatesService.moreReplicatesNeeded(CHAIN_TASK_ID, 2, maxExecutionTime);
        assertThat(res).isTrue();
    }

    @Test
    public void shouldNeedMoreReplicates2(){
        final long maxExecutionTime = 60000;
        Replicate runningReplicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        runningReplicate.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        Replicate notRespondingReplicate1 = mock(Replicate.class);
        when(notRespondingReplicate1.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(true);
        when(notRespondingReplicate1.getCurrentStatus()).thenReturn(ReplicateStatus.RUNNING);
        Replicate notRespondingReplicate2 = mock(Replicate.class);
        when(notRespondingReplicate2.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(true);
        when(notRespondingReplicate2.getCurrentStatus()).thenReturn(ReplicateStatus.RUNNING);


        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(runningReplicate, notRespondingReplicate1, notRespondingReplicate2));
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        // numWorkersNeeded strictly bigger than the number of running replicates
        boolean res = replicatesService.moreReplicatesNeeded(CHAIN_TASK_ID, 2, maxExecutionTime);
        assertThat(res).isTrue();
    }

    @Test
    public void shouldNotNeedMoreReplicates(){
        final long maxExecutionTime = 60000;
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.WORKER_LOST, ReplicateStatusModifier.POOL_MANAGER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate, replicate2));
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        // numWorkersNeeded equals to the number of running replicates
        boolean res = replicatesService.moreReplicatesNeeded(CHAIN_TASK_ID, 1, maxExecutionTime);
        assertThat(res).isFalse();
    }

    @Test
    public void shouldUpdateReplicateStatus(){
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(true);
        when(replicatesRepository.save(replicatesList)).thenReturn(replicatesList);
        String resultHash = "hash";
        when(iexecHubService.getChainContribution(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(ChainContribution.builder()
                .resultHash(resultHash)
                .build()));
        when(web3jService.isBlockAvailable(anyLong())).thenReturn(true);

        ArgumentCaptor<ReplicateUpdatedEvent> argumentCaptor = ArgumentCaptor.forClass(ReplicateUpdatedEvent.class);

        replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        Mockito.verify(applicationEventPublisher, Mockito.times(2))
                .publishEvent(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues().get(0)).isEqualTo(new ReplicateComputedEvent(replicate));
        assertThat(argumentCaptor.getAllValues().get(1)).isEqualTo(new ReplicateUpdatedEvent(replicate.getChainTaskId(), ReplicateStatus.CONTRIBUTED));
        assertThat(replicatesList.getReplicates().get(0).getContributionHash()).isEqualTo(resultHash);
    }

    @Test
    public void shouldNotUpdateReplicateStatusSinceNoReplicateList(){
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);
        Mockito.verify(replicatesRepository, Mockito.times(0))
                .save(any());
        Mockito.verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
    }

    @Test
    public void shouldNotUpdateReplicateStatusSinceNoMatchineReplicate(){
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        // Call on a different worker
        replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_2,
                ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);
        Mockito.verify(replicatesRepository, Mockito.times(0))
                .save(any());
        Mockito.verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
    }

    @Test
    public void shouldNotUpdateReplicateStatusSinceInvalidWorkflowTransition(){
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                REVEALED, ReplicateStatusModifier.WORKER);
        Mockito.verify(replicatesRepository, Mockito.times(0))
                .save(any());
        Mockito.verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
    }

    @Test
    public void shouldNotUpdateReplicateStatusSinceCheckContributionStatusFails(){
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(web3jService.isBlockAvailable(anyLong())).thenReturn(true);
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(false);

        replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        Mockito.verify(replicatesRepository, Mockito.times(0))
                .save(any());
        Mockito.verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
    }

    @Test
    public void shouldNotNotUpdateReplicateStatusSinceCannotGetContribution() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(web3jService.isBlockAvailable(anyLong())).thenReturn(true);
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(true);
        when(iexecHubService.getChainContribution(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.empty());
        when(replicatesRepository.save(replicatesList)).thenReturn(replicatesList);

        replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);

        Mockito.verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
    }

    @Test
    public void shouldNotSetContributionHashSinceRevealing() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.singletonList(replicate));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(web3jService.isBlockAvailable(anyLong())).thenReturn(true);
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(true);
        when(iexecHubService.getChainContribution(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(ChainContribution.builder()
        .resultHash("hash")
        .build()));
        when(replicatesRepository.save(replicatesList)).thenReturn(replicatesList);

        ArgumentCaptor<ReplicateUpdatedEvent> argumentCaptor = ArgumentCaptor.forClass(ReplicateUpdatedEvent.class);

        replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                REVEALED, ReplicateStatusModifier.WORKER);

        Mockito.verify(applicationEventPublisher, Mockito.times(2))
                .publishEvent(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues().get(0)).isEqualTo(new ReplicateComputedEvent(replicate));
        assertThat(argumentCaptor.getAllValues().get(1)).isEqualTo(new ReplicateUpdatedEvent(replicate.getChainTaskId(), REVEALED));
        assertThat(replicatesList.getReplicates().get(0).getContributionHash()).isEmpty();
    }

    @Test
    public void shouldGet2OffChainReplicatesWithStatusContributed(){
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.CONTRIBUTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.WORKER_LOST, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.CONTRIBUTING, ReplicateStatusModifier.WORKER);
        replicate2.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        assertThat(replicatesService.getNbOffChainReplicatesWithStatus(CHAIN_TASK_ID, CONTRIBUTED)).isEqualTo(2);
    }

    @Test
    public void shouldGet1OffChainReplicatesWithStatusContributed(){
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.CONTRIBUTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.WORKER_LOST, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.CONTRIBUTING, ReplicateStatusModifier.WORKER);
        replicate2.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        assertThat(replicatesService.getNbOffChainReplicatesWithStatus(CHAIN_TASK_ID, CONTRIBUTED)).isEqualTo(1);
    }

    @Test
    public void shouldGet0OffChainReplicatesWithStatusContributed(){
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.CONTRIBUTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.WORKER_LOST, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.CONTRIBUTING, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        assertThat(replicatesService.getNbOffChainReplicatesWithStatus(CHAIN_TASK_ID, CONTRIBUTED)).isEqualTo(0);
    }

    @Test
    public void shouldFindReplicateContributedOnchain() {
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any()))
                .thenReturn(true);
    }

    @Test
    public void shouldNotFindReplicateContributedOnchain() {
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any()))
                .thenReturn(false);
    }

    @Test
    public void shouldFindReplicateRevealedOnchain() {
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any()))
                .thenReturn(true);
    }

    @Test
    public void shouldNotFindReplicateRevealedOnchain() {
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any()))
                .thenReturn(true);
    }

    @Test
    public void shouldReturnFalseSinceCouldNotGetEIP712Challenge() {
        when(iexecHubService.isPublicResult(CHAIN_TASK_ID, 0)).thenReturn(false);
        when(resultRepoService.getChallenge()).thenReturn(Optional.empty());

        boolean isResultUploaded = replicatesService.isResultUploaded(CHAIN_TASK_ID);

        assertThat(isResultUploaded).isFalse();
    }

    @Test
    public void shouldReturnFalseSinceCouldNotBuildAuthorizationToken() {

        Eip712Challenge eip712Challenge = new Eip712Challenge("dummyChallenge", 123);
        Credentials credentialsMock = mock(Credentials.class);

        when(iexecHubService.isPublicResult(CHAIN_TASK_ID, 0)).thenReturn(false);
        when(resultRepoService.getChallenge()).thenReturn(Optional.of(eip712Challenge));
        when(credentialsService.getCredentials()).thenReturn(credentialsMock);

        boolean isResultUploaded = replicatesService.isResultUploaded(CHAIN_TASK_ID);

        assertThat(isResultUploaded).isFalse();
    }
}