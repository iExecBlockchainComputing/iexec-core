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

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;
import java.util.stream.Collectors;

import static com.iexec.common.replicate.ReplicateStatus.*;

@Data
@Document
@NoArgsConstructor
public class ReplicatesList {

    @Id
    private String id;

    @Version
    private Long version;

    @Indexed(unique = true)
    private String chainTaskId;

    private List<Replicate> replicates;

    public ReplicatesList(String chainTaskId) {
        this.chainTaskId = chainTaskId;
        this.replicates = new ArrayList<>();
    }

    public ReplicatesList(String chainTaskId, List<Replicate> replicates) {
        this.chainTaskId = chainTaskId;
        this.replicates = replicates;
    }

    /**
     * Computes the number of replicates in the {@link ReplicateStatus#CONTRIBUTED} status
     * that have the right contribution hash.
     * <p>
     * Note this method won't retrieve the replicates list, so it could be static.
     * For test purposes - i.e. mocking -, it is not.
     *
     * @param contributionHash Valid hash of the final result.
     * @return Number of winners who had contributed with the right hash.
     */
    public int getNbValidContributedWinners(String contributionHash) {
        int nbValidWinners = 0;
        for (Replicate replicate : replicates) {
            Optional<ReplicateStatus> oStatus = replicate.getLastRelevantStatus();
            if (oStatus.isPresent() && oStatus.get().equals(CONTRIBUTED)
                    && contributionHash.equals(replicate.getContributionHash())) {
                nbValidWinners++;
            }
        }
        return nbValidWinners;
    }

    public int getNbReplicatesWithCurrentStatus(ReplicateStatus... listStatus) {
        int nbReplicates = 0;
        for (Replicate replicate : replicates) {
            for (ReplicateStatus status : listStatus) {
                if (replicate.getCurrentStatus().equals(status)) {
                    nbReplicates++;
                }
            }
        }
        return nbReplicates;
    }

    public int getNbReplicatesWithLastRelevantStatus(ReplicateStatus... listStatus) {
        int nbReplicates = 0;
        for (Replicate replicate : replicates) {
            for (ReplicateStatus status : listStatus) {
                if (Objects.equals(replicate.getLastRelevantStatus().orElse(null), status)) {
                    nbReplicates++;
                }
            }
        }
        return nbReplicates;
    }

    public int getNbReplicatesContainingStatus(ReplicateStatus... listStatus) {
        Set<String> addressReplicates = new HashSet<>();
        for (Replicate replicate : replicates) {
            List<ReplicateStatus> listReplicateStatus = replicate.getStatusUpdateList().stream()
                    .map(ReplicateStatusUpdate::getStatus)
                    .collect(Collectors.toList());
            for (ReplicateStatus status : listStatus) {
                if (listReplicateStatus.contains(status)) {
                    addressReplicates.add(replicate.getWalletAddress());
                }
            }
        }
        return addressReplicates.size();
    }

    public Optional<Replicate> getRandomReplicateWithRevealStatus() {
        final ArrayList<Replicate> clonedReplicates = new ArrayList<>(replicates);
        Collections.shuffle(clonedReplicates);

        for (Replicate replicate : clonedReplicates) {
            if (replicate.getCurrentStatus().equals(REVEALED)) {
                return Optional.of(replicate);
            }
        }

        return Optional.empty();
    }

    public Optional<Replicate> getReplicateWithResultUploadedStatus() {
        for (Replicate replicate : replicates) {

            boolean isStatusResultUploaded = replicate.getCurrentStatus().equals(RESULT_UPLOADED);
            boolean isStatusResultUploadedBeforeWorkerLost = replicate.isStatusBeforeWorkerLostEqualsTo(RESULT_UPLOADED);

            if (isStatusResultUploaded || isStatusResultUploadedBeforeWorkerLost) {
                return Optional.of(replicate);
            }
        }

        return Optional.empty();
    }

    public boolean hasWorkerAlreadyParticipated(String walletAddress) {
        return getReplicateOfWorker(walletAddress).isPresent();
    }

    public Optional<Replicate> getReplicateOfWorker(String workerWalletAddress) {
        for (Replicate replicate : replicates) {
            if (replicate.getWalletAddress().equals(workerWalletAddress)) {
                return Optional.of(replicate);
            }
        }
        return Optional.empty();
    }
}
