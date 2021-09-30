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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.replicate.ReplicateStatusUpdate;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusUpdate.*;


@Data
@NoArgsConstructor
public class Replicate {

    private List<ReplicateStatusUpdate> statusUpdateList;
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
        this.statusUpdateList = new ArrayList<>();
        // a new replicate should only be create by the scheduler
        this.statusUpdateList.add(poolManagerRequest(CREATED));
        this.contributionHash = "";
    }

    @JsonIgnore
    public ReplicateStatus getCurrentStatus() {
        return this.getLatestStatusUpdate().getStatus();
    }

    @JsonIgnore
    public Optional<ReplicateStatus> getLastRelevantStatus() {  // FIXME: remove Optional and add a no-args constructor
        // ignore cases like: WORKER_LOST and RECOVERING

        List<ReplicateStatus> statusList = getStatusUpdateList().stream()
                .map(ReplicateStatusUpdate::getStatus)
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
        return this.getStatusUpdateList().get(this.getStatusUpdateList().size() - 2).getStatus();
    }

    @JsonIgnore
    private ReplicateStatusUpdate getLatestStatusUpdate() {
        return this.getStatusUpdateList().get(this.getStatusUpdateList().size() - 1);
    }

    public boolean updateStatus(ReplicateStatus newStatus, ReplicateStatusModifier modifier) {
        ReplicateStatusUpdate statusUpdate = new ReplicateStatusUpdate(newStatus, modifier);
        return statusUpdateList.add(statusUpdate);
    }

    public boolean updateStatus(ReplicateStatus newStatus, ReplicateStatusCause cause,
                                ReplicateStatusModifier modifier, ChainReceipt  chainReceipt) {
        ReplicateStatusDetails details = ReplicateStatusDetails.builder()
                .chainReceipt(chainReceipt)
                .cause(cause)
                .build();

        ReplicateStatusUpdate statusUpdate = new ReplicateStatusUpdate(newStatus, modifier, details);
        return statusUpdateList.add(statusUpdate);
    }

    public boolean updateStatus(ReplicateStatusUpdate statusUpdate) {
        return statusUpdateList.add(statusUpdate);
    }

    public String getContributionHash() {
        return contributionHash;
    }

    public void setContributionHash(String contributionHash) {
        this.contributionHash = contributionHash;
    }

    public int getCredibility() {
        return credibility;
    }

    public void setCredibility(int credibility) {
        this.credibility = credibility + 1;
    }

    public boolean containsStatus(ReplicateStatus replicateStatus) {
        for (ReplicateStatusUpdate replicateStatusUpdate : this.getStatusUpdateList()) {
            if (replicateStatusUpdate.getStatus().equals(replicateStatus)) {
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
        Date creationDate = this.getStatusUpdateList().get(0).getDate();
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
        return ReplicateStatus.isRecoverable(currentStatus.get());
    }

    public boolean isBeforeStatus(ReplicateStatus status) {
        Optional<ReplicateStatus> currentStatus = getLastRelevantStatus();
        if (!getLastRelevantStatus().isPresent()) return false;
        return currentStatus.get().ordinal() < status.ordinal();
    }
}
