package com.iexec.core.replicate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusChange;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

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
        this.statusChangeList.add(new ReplicateStatusChange(ReplicateStatus.CREATED));
        this.contributionHash = "";
    }

    @JsonIgnore
    public ReplicateStatus getCurrentStatus() {
        return this.getLatestStatusChange().getStatus();
    }

    @JsonIgnore
    private ReplicateStatusChange getLatestStatusChange() {
        return this.getStatusChangeList().get(this.getStatusChangeList().size() - 1);
    }

    public boolean updateStatus(ReplicateStatus newStatus) {
        return statusChangeList.add(new ReplicateStatusChange(newStatus));
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
}
