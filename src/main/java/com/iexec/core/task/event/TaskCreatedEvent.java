package com.iexec.core.task.event;

import com.iexec.core.task.Task;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TaskCreatedEvent {

    private Task task;
}
