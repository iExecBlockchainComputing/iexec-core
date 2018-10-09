package com.iexec.core.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusChange;
import org.junit.Test;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(replicate.getLatestStatus()).isEqualTo(ReplicateStatus.CREATED);

        replicate.updateStatus(ReplicateStatus.RUNNING);
        assertThat(replicate.getStatusChangeList().size()).isEqualTo(2);
        assertThat(replicate.getLatestStatus()).isEqualTo(ReplicateStatus.RUNNING);
    }

}
