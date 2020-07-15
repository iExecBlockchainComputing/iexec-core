package com.iexec.core.stdout;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface StdoutRepository extends MongoRepository<TaskStdout, String> {

    Optional<TaskStdout> findOneByChainTaskId(String chainTaskId);

    @Query(value = "{ chainTaskId: ?0 }", fields = "{ replicateStdoutList: { $elemMatch: { walletAddress: ?1 } } }")
    Optional<TaskStdout> findByChainTaskIdAndWalletAddress(String chainTaskId, String walletAddress);

    void deleteByChainTaskIdIn(List<String> chainTaskIds);
}
