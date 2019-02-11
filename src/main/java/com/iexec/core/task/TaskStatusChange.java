package com.iexec.core.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.Date;

import com.iexec.common.chain.ChainReceipt;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskStatusChange {

    private Date date;
    private TaskStatus status;
    private ChainReceipt chainReceipt;

    TaskStatusChange(TaskStatus status){
        this(status, null);
    }

    TaskStatusChange(TaskStatus status, ChainReceipt chainReceipt){
        this.date = new Date();
        this.status = status;
        this.chainReceipt = chainReceipt;
    }

    public TaskStatusChange(Date date, TaskStatus status) {
        this(date, status, null);
    }
}
