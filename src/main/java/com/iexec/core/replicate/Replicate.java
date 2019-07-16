package com.iexec.core.replicate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.replicate.ReplicateStatusChange;
import com.iexec.common.replicate.ReplicateStatusModifier;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;


@Data
@NoArgsConstructor
public class Replicate {

    private List<ReplicateStatusChange> statusChangeList;
    private String walletAddress;
    private String resultLink;
    private String chainCallbackData;
    private String chainTaskId;
    private String contributionHash;
    private int credibility;
    private int workerWeight;

    public Replicate(String walletAddress, String chainTaskId) {
        this.chainTaskId = chainTaskId;
        this.walletAddress = walletAddress;
        this.statusChangeList = new ArrayList<>();
        // a new replicate should only be create by the scheduler
        this.statusChangeList.add(new ReplicateStatusChange(ReplicateStatus.CREATED, ReplicateStatusModifier.POOL_MANAGER));
        this.contributionHash = "";
    }

    @JsonIgnore
    public ReplicateStatus getCurrentStatus() {
        return this.getLatestStatusChange().getStatus();
    }

    @JsonIgnore
    public Optional<ReplicateStatus> getLastRelevantStatus() {
        // ignore cases like: WORKER_LOST and RECOVERING

        List<ReplicateStatus> statusList = getStatusChangeList().stream()
                .map(ReplicateStatusChange::getStatus)
                .collect(Collectors.toList());

        List<ReplicateStatus> ignoredStatuses = Arrays.asList(
                ReplicateStatus.WORKER_LOST,
                ReplicateStatus.RECOVERING);

        for (int i = statusList.size() - 1; i >= 0; i--) {
            if (!ignoredStatuses.contains(statusList.get(i))) {
                return Optional.of(statusList.get(i));
            }
        }

        return Optional.empty();
    }

    @JsonIgnore
    public ReplicateStatus getLastButOneStatus() {
        return this.getStatusChangeList().get(this.getStatusChangeList().size() - 2).getStatus();
    }

    @JsonIgnore
    private ReplicateStatusChange getLatestStatusChange() {
        return this.getStatusChangeList().get(this.getStatusChangeList().size() - 1);
    }

    public boolean updateStatus(ReplicateStatus newStatus, ReplicateStatusModifier modifier) {
        return statusChangeList.add(new ReplicateStatusChange(newStatus, modifier));
    }

    public boolean updateStatus(ReplicateStatus newStatus, ReplicateStatusCause newStatusCause, ReplicateStatusModifier modifier, ChainReceipt  chainReceipt) {
        return statusChangeList.add(new ReplicateStatusChange(newStatus, newStatusCause, modifier, chainReceipt));
    }

    public String getContributionHash() {
        return contributionHash;
    }

    void setContributionHash(String contributionHash) {
        this.contributionHash = contributionHash;
    }

    public int getCredibility() {
        return credibility;
    }

    public void setCredibility(int credibility) {
        this.credibility = credibility + 1;
    }

    public boolean containsStatus(ReplicateStatus replicateStatus) {
        for (ReplicateStatusChange replicateStatusChange : this.getStatusChangeList()) {
            if (replicateStatusChange.getStatus().equals(replicateStatus)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsContributedStatus() {
        return containsStatus(ReplicateStatus.CONTRIBUTED);
    }

    public boolean containsRevealedStatus() {
        return containsStatus(ReplicateStatus.REVEALED);
    }

    public boolean isCreatedMoreThanNPeriodsAgo(int numberPeriod, long maxExecutionTime) {
        Date creationDate = this.getStatusChangeList().get(0).getDate();
        Date numberPeriodsAfterCreationDate = new Date(creationDate.getTime() + numberPeriod * maxExecutionTime);
        Date now = new Date();

        return now.after(numberPeriodsAfterCreationDate);
    }

    public boolean isLostAfterStatus(ReplicateStatus status) {
        return getCurrentStatus() == ReplicateStatus.WORKER_LOST &&
                getLastButOneStatus() == status;
    }

    public boolean isBusyComputing() {
        return ReplicateStatus.getSuccessStatusesBeforeComputed().contains(getCurrentStatus());
    }

    public boolean isRecoverable() {
        Optional<ReplicateStatus> currentStatus = getLastRelevantStatus();
        if (!currentStatus.isPresent()) return false;
        return ReplicateStatus.isRecoverableStatus(currentStatus.get());
    }

    public boolean isBeforeStatus(ReplicateStatus status) {
        Optional<ReplicateStatus> currentStatus = getLastRelevantStatus();
        if (!getLastRelevantStatus().isPresent()) return false;
        return currentStatus.get().ordinal() < status.ordinal();
    }
}
