package com.iexec.core.replicate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusChange;
import com.iexec.common.replicate.ReplicateStatusModifier;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.iexec.common.replicate.ReplicateStatus.CONTRIBUTED;
import static com.iexec.common.replicate.ReplicateStatus.REVEALED;

@Data
@NoArgsConstructor
public class Replicate {

    private List<ReplicateStatusChange> statusChangeList;
    private String walletAddress;
    private String resultUri;
    private String chainTaskId;
    private String contributionHash;
    private int credibility;

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
        return containsStatus(CONTRIBUTED);
    }

    public boolean isCreatedMoreThanNPeriodsAgo(int numberPeriod, Date timeRef) {
        Date creationDate = this.getStatusChangeList().get(0).getDate();
        Date numberPeriodsAfterCreationDate = new Date(creationDate.getTime() + numberPeriod * timeRef.getTime());
        Date now = new Date();

        return now.after(numberPeriodsAfterCreationDate);
    }

    public boolean isBusyComputing() {
        return ReplicateStatus.getSuccessStatusesBeforeComputed().contains(getCurrentStatus());
    }

}
