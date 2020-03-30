package com.iexec.core.replicate;

import com.iexec.common.chain.ChainContribution;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.core.chain.CredentialsService;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.result.ResultService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.*;
import org.springframework.context.ApplicationEventPublisher;
import org.web3j.crypto.Credentials;

import java.util.*;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusModifier.*;
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
    @Mock private ResultService resultService;

    @InjectMocks
    private ReplicatesService replicatesService;


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldCreateNewReplicate() {
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
    public void shouldNotCreateNewReplicate() {
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
    public void shouldCreateEmptyReplicateList() {
        replicatesService.createEmptyReplicateList(CHAIN_TASK_ID);

        Mockito.verify(replicatesRepository, Mockito.times(1)).save(new ReplicatesList(CHAIN_TASK_ID));
    }

    @Test
    public void shouldGetReplicates() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);

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
    public void shouldNotGetReplicate1() {
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        assertThat(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).isEqualTo(Optional.empty());
    }

    @Test
    public void shouldNotGetReplicate2() {
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
    public void shouldHaveWorkerAlreadyContributed() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(STARTING, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        assertThat(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1)).isTrue();
        assertThat(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_2)).isTrue();
    }

    @Test
    public void shouldNotHaveWorkerAlreadyContributed() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(STARTING, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        assertThat(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_3)).isFalse();
    }

    @Test
    public void shouldGetCorrectNbReplicatesWithOneStatus() {
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
    public void shouldGetCorrectNbReplicatesWithMultipleStatus() {
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
    public void shouldGetCorrectNbReplicatesContainingOneStatus() {
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
    public void shouldGetCorrectNbReplicatesContainingMultipleStatus() {
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
    public void shouldGetReplicateWithRevealStatus() {
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
    public void shouldNotGetReplicateWithRevealStatusSinceEmptyReplicatesList() {
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        Optional<Replicate> optional = replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID);
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    public void shouldNotGetReplicateWithRevealStatusWithNonEmptyList() {
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
    public void shouldUpdateReplicateStatus(){
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
    }

    @Test
    public void shouldNotUpdateReplicateStatusSinceNoReplicateList(){
        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, new ReplicateStatusUpdate(REVEALING));
        Mockito.verify(replicatesRepository, Mockito.times(0))
                .save(any());
        Mockito.verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
    }

    @Test
    public void shouldNotUpdateReplicateStatusSinceNoMatchingReplicate(){
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
    public void shouldNotUpdateReplicateStatusSinceInvalidWorkflowTransition() {
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
    public void shouldNotUpdateReplicateStatusSinceWrongOnChainStatus(){
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
    public void shouldNotUpdateReplicateStatusToContributedSinceGetContributionFailed() {
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
    public void shouldNotUpdateReplicateStatusToContributedSinceCannotGetWorkerWeight() {
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
    public void shouldNotUpdateReplicateStatusToResultUploadingSinceResultIsNotUploaded() {
        //TODO After having moved isResultUploaded() method to another class
    }

    @Test
    public void shouldNotUpdateReplicateStatusToResultUploadingSinceResultLinkMissing() {
        //TODO After having moved isResultUploaded() method to another class
    }

    @Test
    public void shouldNotSetContributionHashSinceRevealing() {
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
    public void shouldGet2OffChainReplicatesWithStatusContributed(){
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(CONTRIBUTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(WORKER_LOST, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(CONTRIBUTING, ReplicateStatusModifier.WORKER);
        replicate2.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        assertThat(replicatesService.getNbOffChainReplicatesWithStatus(CHAIN_TASK_ID, CONTRIBUTED)).isEqualTo(2);
    }

    @Test
    public void shouldGet1OffChainReplicatesWithStatusContributed(){
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(CONTRIBUTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(WORKER_LOST, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(CONTRIBUTING, ReplicateStatusModifier.WORKER);
        replicate2.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        assertThat(replicatesService.getNbOffChainReplicatesWithStatus(CHAIN_TASK_ID, CONTRIBUTED)).isEqualTo(1);
    }

    @Test
    public void shouldGet0OffChainReplicatesWithStatusContributed(){
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(CONTRIBUTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(WORKER_LOST, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(CONTRIBUTING, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        when(replicatesRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        assertThat(replicatesService.getNbOffChainReplicatesWithStatus(CHAIN_TASK_ID, CONTRIBUTED)).isEqualTo(0);
    }

    @Test
    public void shouldFindReplicateContributedOnchain() {
        when(iexecHubService.repeatIsContributedTrue(any(), any()))
                .thenReturn(true);
    }

    @Test
    public void shouldNotFindReplicateContributedOnchain() {
        when(iexecHubService.repeatIsContributedTrue(any(), any()))
                .thenReturn(false);
    }

    @Test
    public void shouldFindReplicateRevealedOnchain() {
        when(iexecHubService.repeatIsContributedTrue(any(), any()))
                .thenReturn(true);
    }

    @Test
    public void shouldNotFindReplicateRevealedOnchain() {
        when(iexecHubService.repeatIsContributedTrue(any(), any()))
                .thenReturn(true);
    }

    @Test
    public void shouldReturnFalseSinceCouldNotGetEIP712Challenge() {
        when(iexecHubService.isPublicResult(CHAIN_TASK_ID, 0)).thenReturn(false);
        when(resultService.getChallenge()).thenReturn(Optional.empty());

        boolean isResultUploaded = replicatesService.isResultUploaded(CHAIN_TASK_ID);

        assertThat(isResultUploaded).isFalse();
    }

    @Test
    public void shouldReturnFalseSinceCouldNotBuildAuthorizationToken() {

        Eip712Challenge eip712Challenge = new Eip712Challenge("dummyChallenge", 123);
        Credentials credentialsMock = mock(Credentials.class);

        when(iexecHubService.isPublicResult(CHAIN_TASK_ID, 0)).thenReturn(false);
        when(resultService.getChallenge()).thenReturn(Optional.of(eip712Challenge));
        when(credentialsService.getCredentials()).thenReturn(credentialsMock);

        boolean isResultUploaded = replicatesService.isResultUploaded(CHAIN_TASK_ID);

        assertThat(isResultUploaded).isFalse();
    }
}