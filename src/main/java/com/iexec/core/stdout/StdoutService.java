package com.iexec.core.stdout;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class StdoutService {

    private StdoutRepository stdoutRepository;

    public StdoutService(StdoutRepository stdoutRepository) {
        this.stdoutRepository = stdoutRepository;
    }

    public void addReplicateStdout(String chainTaskId, String walletAddress, String stdout) {
        ReplicateStdout newReplicateStdout = new ReplicateStdout(walletAddress, stdout);
        TaskStdout taskStdout = getTaskStdout(chainTaskId).orElse(new TaskStdout(chainTaskId));
        if (taskStdout.containsWalletAddress(walletAddress)) {
            return;
        }
        taskStdout.getReplicateStdoutList().add(newReplicateStdout);
        stdoutRepository.save(taskStdout);
    }

    public Optional<TaskStdout> getTaskStdout(String chainTaskId) {
        return stdoutRepository.findOneByChainTaskId(chainTaskId);
    }

    public Optional<TaskStdout> getReplicateStdout(String chainTaskId, String walletAddress) {
        return stdoutRepository.findByChainTaskIdAndWalletAddress(chainTaskId, walletAddress);
    }

    public void delete(List<String> chainTaskIds) {
        stdoutRepository.deleteByChainTaskIdIn(chainTaskIds);
    }
}
