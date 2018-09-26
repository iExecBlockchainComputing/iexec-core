package com.iexec.core.tasks;

import java.util.Date;

public class TaskStatusChange {

    private Date date;
    private TaskStatus status;

    public TaskStatusChange() {}

    public TaskStatusChange(Date date, TaskStatus status) {
        this.date = date;
        this.status = status;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }
}
