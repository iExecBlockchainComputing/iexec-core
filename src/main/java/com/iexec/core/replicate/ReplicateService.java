package com.iexec.core.replicate;

import com.iexec.common.replicate.ReplicateModel;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ReplicateService {

    private TaskService taskService;

    @Autowired
    public ReplicateService(TaskService taskService) {
        this.taskService = taskService;
    }

    public Optional<ReplicateModel> entity2Dto(Replicate replicate) {
        Optional<Task> optional = taskService.getTask(replicate.getTaskId());
        if (!optional.isPresent()) {
            return Optional.empty();
        }
        Task task = optional.get();

        return Optional.of(ReplicateModel.builder()
                .taskId(replicate.getTaskId())
                .workerAddress(replicate.getWorkerName())
                .dappType(task.getDappType())
                .dappName(task.getDappName())
                .cmd(task.getCommandLine())
                .replicateStatus(replicate.getStatusList().get(replicate.getStatusList().size() - 1).getStatus())
                .build());
    }

}
