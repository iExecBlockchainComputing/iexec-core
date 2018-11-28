package com.iexec.core.task;

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
