package com.iexec.core.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusChange;
import org.assertj.core.api.Java6Assertions;
import org.junit.Test;

import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

        replicate.updateStatus(ReplicateStatus.RUNNING);
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

        replicate.updateStatus(ReplicateStatus.RUNNING);
        assertThat(replicate.getStatusChangeList().size()).isEqualTo(2);
        assertThat(replicate.getCurrentStatus()).isEqualTo(ReplicateStatus.RUNNING);
    }


    @Test
    public void shouldReturnTrueWhenContributed(){
        Replicate replicate = new Replicate("0x1", "taskId");
        replicate.updateStatus(ReplicateStatus.RUNNING);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTING);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTED);
        replicate.updateStatus(ReplicateStatus.REVEALING);
        replicate.updateStatus(ReplicateStatus.REVEALED);

        assertThat(replicate.containsContributedStatus()).isTrue();
    }

    @Test
    public void shouldReturnFalseWhenContributedMissing(){
        Replicate replicate = new Replicate("0x1", "taskId");
        replicate.updateStatus(ReplicateStatus.RUNNING);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTING);
        replicate.updateStatus(ReplicateStatus.REVEALING);
        replicate.updateStatus(ReplicateStatus.REVEALED);

        assertThat(replicate.containsContributedStatus()).isFalse();
    }

    @Test
    public void shouldBeCreatedLongAgo(){
        Date timeRef = new Date(60000);
        Date now = new Date();
        Replicate replicate = new Replicate("0x1", "taskId");
        ReplicateStatusChange oldCreationDate = replicate.getStatusChangeList().get(0);
        oldCreationDate.setDate(new Date(now.getTime() - 3 * timeRef.getTime()));
        replicate.setStatusChangeList(Collections.singletonList(oldCreationDate));

        assertThat(replicate.isCreatedLongAgo(timeRef)).isTrue();
    }

    @Test
    public void shouldNotBeCreatedLongAgo(){
        Date timeRef = new Date(60000);
        Date now = new Date();
        Replicate replicate = new Replicate("0x1", "taskId");
        ReplicateStatusChange oldCreationDate = replicate.getStatusChangeList().get(0);
        oldCreationDate.setDate(new Date(now.getTime() - timeRef.getTime()));
        replicate.setStatusChangeList(Collections.singletonList(oldCreationDate));

        assertThat(replicate.isCreatedLongAgo(timeRef)).isFalse();
    }

    @Test
    public void shouldReturnTrueForIsContributingPeriodTooLong(){
        final Date timeRef = new Date(60000);
        Replicate replicate = mock(Replicate.class);

        when(replicate.containsContributedStatus()).thenReturn(false);
        when(replicate.isCreatedLongAgo(timeRef)).thenReturn(true);

        when(replicate.isContributingPeriodTooLong(timeRef)).thenCallRealMethod();
        assertThat(replicate.isContributingPeriodTooLong(timeRef)).isTrue();
    }


    @Test
    public void shouldReturnFalseIfContributed1(){
        final Date timeRef = new Date(60000);
        Replicate replicate = mock(Replicate.class);

        when(replicate.containsContributedStatus()).thenReturn(true);
        when(replicate.isCreatedLongAgo(timeRef)).thenReturn(false);

        when(replicate.isContributingPeriodTooLong(timeRef)).thenCallRealMethod();
        assertThat(replicate.isContributingPeriodTooLong(timeRef)).isFalse();
    }

    @Test
    public void shouldReturnFalseIfContributed2(){
        final Date timeRef = new Date(60000);
        Replicate replicate = mock(Replicate.class);

        when(replicate.containsContributedStatus()).thenReturn(true);
        when(replicate.isCreatedLongAgo(timeRef)).thenReturn(true);

        when(replicate.isContributingPeriodTooLong(timeRef)).thenCallRealMethod();
        assertThat(replicate.isContributingPeriodTooLong(timeRef)).isFalse();
    }


    @Test
    public void shouldReturnFalseIfNotContributedButStillHaveTime(){
        final Date timeRef = new Date(60000);
        Replicate replicate = mock(Replicate.class);

        when(replicate.containsContributedStatus()).thenReturn(false);
        when(replicate.isCreatedLongAgo(timeRef)).thenReturn(false);

        when(replicate.isContributingPeriodTooLong(timeRef)).thenCallRealMethod();
        assertThat(replicate.isContributingPeriodTooLong(timeRef)).isFalse();
    }

}
