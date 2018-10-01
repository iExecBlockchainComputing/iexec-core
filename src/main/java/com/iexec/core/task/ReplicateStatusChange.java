package com.iexec.core.task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplicateStatusChange {

    private Date date;
    private ReplicateStatus status;


    public ReplicateStatusChange(ReplicateStatus status) {
        this.date = new Date();
        this.status = status;
    }
}
