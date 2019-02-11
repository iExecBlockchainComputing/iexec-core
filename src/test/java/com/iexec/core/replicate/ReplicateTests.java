package com.iexec.core.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusChange;
import com.iexec.common.replicate.ReplicateStatusModifier;
import org.junit.Test;

import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ReplicateTests {

    @Test
    public void shouldInitializeStatusProperly(){
        Replicate replicate = new Replicate("worker", "taskId");
        assertThat(replicate.getStatusChangeList().size()).isEqualTo(1);

        ReplicateStatusChange statusChange = replicate.getStatusChangeList().get(0);
        assertThat(statusChange.getStatus()).isEqualTo(ReplicateStatus.CREATED);

        Date now = new Date();
        long duration = now.getTime() - statusChange.getDate().getTime();
        long diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
        assertThat(diffInSeconds).isLessThan(1);
    }

    @Test
    public void shouldUpdateReplicateStatus(){
        Replicate replicate = new Replicate("worker", "taskId");
        assertThat(replicate.getStatusChangeList().size()).isEqualTo(1);

        replicate.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        assertThat(replicate.getStatusChangeList().size()).isEqualTo(2);

        ReplicateStatusChange initialStatus = replicate.getStatusChangeList().get(0);
        assertThat(initialStatus.getStatus()).isEqualTo(ReplicateStatus.CREATED);

        ReplicateStatusChange updatedStatus = replicate.getStatusChangeList().get(1);
        assertThat(updatedStatus.getStatus()).isEqualTo(ReplicateStatus.RUNNING);

        Date now = new Date();
        long duration = now.getTime() - updatedStatus.getDate().getTime();
        long diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
        assertThat(diffInSeconds).isLessThan(1);
    }

    @Test
    public void shouldGetProperLatestStatus(){
        Replicate replicate = new Replicate("worker", "taskId");
        assertThat(replicate.getStatusChangeList().size()).isEqualTo(1);
        assertThat(replicate.getCurrentStatus()).isEqualTo(ReplicateStatus.CREATED);

        replicate.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        assertThat(replicate.getStatusChangeList().size()).isEqualTo(2);
        assertThat(replicate.getCurrentStatus()).isEqualTo(ReplicateStatus.RUNNING);
    }


    @Test
    public void shouldReturnTrueWhenContributed(){
        Replicate replicate = new Replicate("0x1", "taskId");
        replicate.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        assertThat(replicate.containsContributedStatus()).isTrue();
    }

    @Test
    public void shouldReturnFalseWhenContributedMissing(){
        Replicate replicate = new Replicate("0x1", "taskId");
        replicate.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
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
        ReplicateStatusChange oldCreationDate = replicate.getStatusChangeList().get(0);
        oldCreationDate.setDate(new Date(now.getTime() - 3 * maxExecutionTime));
        replicate.setStatusChangeList(Collections.singletonList(oldCreationDate));

        assertThat(replicate.isCreatedMoreThanNPeriodsAgo(2, maxExecutionTime)).isTrue();
    }

    @Test
    public void shouldNotBeCreatedLongAgo(){
        final long maxExecutionTime = 60000;
        Date now = new Date();
        Replicate replicate = new Replicate("0x1", "taskId");
        ReplicateStatusChange oldCreationDate = replicate.getStatusChangeList().get(0);
        oldCreationDate.setDate(new Date(now.getTime() - maxExecutionTime));
        replicate.setStatusChangeList(Collections.singletonList(oldCreationDate));

        assertThat(replicate.isCreatedMoreThanNPeriodsAgo(2, maxExecutionTime)).isFalse();
    }

    @Test
    public void shouldBeBusyComputing() {
        Replicate replicate = new Replicate("worker", "taskId");
        assertThat(replicate.isBusyComputing()).isTrue();
        replicate.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
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
