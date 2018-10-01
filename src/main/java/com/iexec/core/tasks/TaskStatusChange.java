package com.iexec.core.tasks;

import lombok.*;

import java.util.Date;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class TaskStatusChange {

    private Date date;
    private TaskStatus status;

    TaskStatusChange(TaskStatus status){
        this.date = new Date();
        this.status = status;
    }
}
