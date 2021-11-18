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
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.replicate.ReplicateStatusUpdate;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class ReplicateTests {

    @Test
    public void shouldInitializeStatusProperly(){
        Replicate replicate = new Replicate("worker", "taskId");
        assertThat(replicate.getStatusUpdateList().size()).isEqualTo(1);

        ReplicateStatusUpdate statusChange = replicate.getStatusUpdateList().get(0);
        assertThat(statusChange.getStatus()).isEqualTo(ReplicateStatus.CREATED);

        Date now = new Date();
        long duration = now.getTime() - statusChange.getDate().getTime();
        long diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
        assertThat(diffInSeconds).isLessThan(1);
    }

    @Test
    public void shouldUpdateReplicateStatus(){
        Replicate replicate = new Replicate("worker", "taskId");
        assertThat(replicate.getStatusUpdateList().size()).isEqualTo(1);

        // only pool manager sets date of the update
        replicate.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.POOL_MANAGER);
        assertThat(replicate.getStatusUpdateList().size()).isEqualTo(2);

        ReplicateStatusUpdate initialStatus = replicate.getStatusUpdateList().get(0);
        assertThat(initialStatus.getStatus()).isEqualTo(ReplicateStatus.CREATED);

        ReplicateStatusUpdate updatedStatus = replicate.getStatusUpdateList().get(1);
        assertThat(updatedStatus.getStatus()).isEqualTo(ReplicateStatus.STARTING);

        Date now = new Date();
        long duration = now.getTime() - updatedStatus.getDate().getTime();
        long diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
        assertThat(diffInSeconds).isLessThan(1);
    }

    @Test
    public void shouldGetProperLatestStatus(){
        Replicate replicate = new Replicate("worker", "taskId");
        assertThat(replicate.getStatusUpdateList().size()).isEqualTo(1);
        assertThat(replicate.getCurrentStatus()).isEqualTo(ReplicateStatus.CREATED);

        replicate.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.WORKER);
        assertThat(replicate.getStatusUpdateList().size()).isEqualTo(2);
        assertThat(replicate.getCurrentStatus()).isEqualTo(ReplicateStatus.STARTING);
    }


    @Test
    public void shouldReturnTrueWhenContributed(){
        Replicate replicate = new Replicate("0x1", "taskId");
        replicate.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        assertThat(replicate.containsContributedStatus()).isTrue();
    }

    @Test
    public void shouldReturnFalseWhenContributedMissing(){
        Replicate replicate = new Replicate("0x1", "taskId");
        replicate.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        assertThat(replicate.containsContributedStatus()).isFalse();
    }

    @Test
    public void shouldBeCreatedLongAgo(){
        final long maxExecutionTime = 60000;
        Date now = new Date();
        Replicate replicate = new Replicate("0x1", "taskId");
        ReplicateStatusUpdate oldCreationDate = replicate.getStatusUpdateList().get(0);
        oldCreationDate.setDate(new Date(now.getTime() - 3 * maxExecutionTime));
        replicate.setStatusUpdateList(Collections.singletonList(oldCreationDate));

        assertThat(replicate.isCreatedMoreThanNPeriodsAgo(2, maxExecutionTime)).isTrue();
    }

    @Test
    public void shouldNotBeCreatedLongAgo(){
        final long maxExecutionTime = 60000;
        Date now = new Date();
        Replicate replicate = new Replicate("0x1", "taskId");
        ReplicateStatusUpdate oldCreationDate = replicate.getStatusUpdateList().get(0);
        oldCreationDate.setDate(new Date(now.getTime() - maxExecutionTime));
        replicate.setStatusUpdateList(Collections.singletonList(oldCreationDate));

        assertThat(replicate.isCreatedMoreThanNPeriodsAgo(2, maxExecutionTime)).isFalse();
    }

    @Test
    public void shouldBeBusyComputing() {
        Replicate replicate = new Replicate("worker", "taskId");
        assertThat(replicate.isBusyComputing()).isTrue();
        replicate.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.WORKER);
        assertThat(replicate.isBusyComputing()).isTrue();
        replicate.updateStatus(ReplicateStatus.APP_DOWNLOADING, ReplicateStatusModifier.WORKER);
        assertThat(replicate.isBusyComputing()).isTrue();
        replicate.updateStatus(ReplicateStatus.APP_DOWNLOADED, ReplicateStatusModifier.WORKER);
        assertThat(replicate.isBusyComputing()).isTrue();
        replicate.updateStatus(ReplicateStatus.COMPUTING, ReplicateStatusModifier.WORKER);
        assertThat(replicate.isBusyComputing()).isTrue();

        replicate.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        assertThat(replicate.isBusyComputing()).isFalse();
        replicate.updateStatus(ReplicateStatus.CONTRIBUTING, ReplicateStatusModifier.WORKER);
        assertThat(replicate.isBusyComputing()).isFalse();
        replicate.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        assertThat(replicate.isBusyComputing()).isFalse();
        replicate.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);
        assertThat(replicate.isBusyComputing()).isFalse();
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);
        assertThat(replicate.isBusyComputing()).isFalse();
        replicate.updateStatus(ReplicateStatus.COMPLETED, ReplicateStatusModifier.WORKER);
        assertThat(replicate.isBusyComputing()).isFalse();
    }
}
