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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.replicate.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static com.iexec.common.replicate.ReplicateStatus.CREATED;
import static com.iexec.common.replicate.ReplicateStatus.WORKER_LOST;
import static com.iexec.common.replicate.ReplicateStatusUpdate.poolManagerRequest;


@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Replicate {

    // FIXME: should be final
    private List<ReplicateStatusUpdate> statusUpdateList = new ArrayList<>(
            // a new replicate should only be created by the scheduler
            List.of(poolManagerRequest(CREATED))
    );
    private String walletAddress;
    private String resultLink;
    private String chainCallbackData;
    private String chainTaskId;
    private String contributionHash;
    private int workerWeight;
    private boolean appComputeLogsPresent;

    public Replicate(String walletAddress, String chainTaskId) {
        this.chainTaskId = chainTaskId;
        this.walletAddress = walletAddress;
        this.contributionHash = "";
    }

    @JsonIgnore
    public ReplicateStatus getCurrentStatus() {
        return this.getLatestStatusUpdate().getStatus();
    }

    @JsonIgnore
    public ReplicateStatus getLastRelevantStatus() {
        // ignore cases like: WORKER_LOST and RECOVERING

        List<ReplicateStatus> statusList = statusUpdateList.stream()
                .map(ReplicateStatusUpdate::getStatus)
                .collect(Collectors.toList());

        List<ReplicateStatus> ignoredStatuses = Arrays.asList(
                ReplicateStatus.WORKER_LOST,
                ReplicateStatus.RECOVERING);

        for (int i = statusList.size() - 1; i >= 0; i--) {
            if (!ignoredStatuses.contains(statusList.get(i))) {
                return statusList.get(i);
            }
        }

        throw new NoReplicateStatusException(chainTaskId);
    }

    @JsonIgnore
    public ReplicateStatus getLastButOneStatus() {
        return statusUpdateList.get(statusUpdateList.size() - 2).getStatus();
    }

    @JsonIgnore
    private ReplicateStatusUpdate getLatestStatusUpdate() {
        return statusUpdateList.get(statusUpdateList.size() - 1);
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

    public boolean containsStatus(ReplicateStatus replicateStatus) {
        for (ReplicateStatusUpdate replicateStatusUpdate : statusUpdateList) {
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
        Date creationDate = statusUpdateList.get(0).getDate();
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
        ReplicateStatus currentStatus = getLastRelevantStatus();
        return ReplicateStatus.isRecoverable(currentStatus);
    }

    public boolean isBeforeStatus(ReplicateStatus status) {
        ReplicateStatus currentStatus = getLastRelevantStatus();
        return currentStatus.ordinal() < status.ordinal();
    }

    boolean isStatusBeforeWorkerLostEqualsTo(ReplicateStatus status) {
        int size = statusUpdateList.size();
        return size >= 2
                && statusUpdateList.get(size - 1).getStatus().equals(WORKER_LOST)
                && statusUpdateList.get(size - 2).getStatus().equals(status);
    }
}
