package com.iexec.core.tasks;

import java.util.Date;

public class ReplicateStatusChange {

    private Date date;
    private ReplicateStatus status;

    public ReplicateStatusChange() {
    }

    public ReplicateStatusChange(ReplicateStatus status) {
        this.date = new Date();
        this.status = status;
    }

    public ReplicateStatusChange(Date date, ReplicateStatus status) {
        this.date = date;
        this.status = status;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public ReplicateStatus getStatus() {
        return status;
    }

    public void setStatus(ReplicateStatus status) {
        this.status = status;
    }
}
