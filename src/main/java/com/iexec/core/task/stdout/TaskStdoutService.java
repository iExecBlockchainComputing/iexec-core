package com.iexec.core.task.stdout;

import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class TaskStdoutService {

    private TaskStdoutRepository taskStdoutRepository;

    public TaskStdoutService(TaskStdoutRepository taskStdoutRepository) {
        this.taskStdoutRepository = taskStdoutRepository;
    }

    public Optional<TaskStdout> getTaskStdout(String chainTaskId) {
        return taskStdoutRepository.findOneByChainTaskId(chainTaskId);
    }

    public Optional<ReplicateStdout> getReplicateStdout(String chainTaskId, String walletAddress) {
        Optional<TaskStdout> replicateStdout = taskStdoutRepository.findByChainTaskIdAndWalletAddress(chainTaskId, walletAddress);
        if (replicateStdout.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(replicateStdout.get().getReplicateStdoutList().get(0));
    }

    public void addReplicateStdout(String chainTaskId, String walletAddress, String stdout) {
        ReplicateStdout newReplicateStdout = new ReplicateStdout(walletAddress, stdout);
        TaskStdout taskStdout = getTaskStdout(chainTaskId).orElse(new TaskStdout(chainTaskId));
        if (taskStdout.containsWalletAddress(walletAddress)) {
            return;
        }
        taskStdout.getReplicateStdoutList().add(newReplicateStdout);
        taskStdoutRepository.save(taskStdout);
    }
}
