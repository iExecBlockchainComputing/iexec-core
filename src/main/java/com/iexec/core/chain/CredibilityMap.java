package com.iexec.core.chain;

import com.iexec.core.replicate.Replicate;
import com.iexec.core.task.Task;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class CredibilityMap {

    private final int bestCredibility;
    private final String consensusValue;

    public CredibilityMap(Task task) {

        // populate map
        Map<String, Integer> unsortedMap = new HashMap<>();
        for (Replicate replicate : task.getReplicates()) {
            String hash = replicate.getResultHash();
            int credibility = replicate.getCredibility();

            if (!unsortedMap.containsKey(hash)) {
                unsortedMap.put(hash, credibility);
            } else {
                Integer totalCredibility = unsortedMap.get(hash);
                unsortedMap.replace(hash, totalCredibility + credibility);
            }
        }

        // sort it
        final Map<String, Integer> hashToCredibility = unsortedMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        // retrieve values of the best cluster
        Map.Entry<String, Integer> bestCluster = hashToCredibility.entrySet().iterator().next();
        this.bestCredibility = bestCluster.getValue();
        this.consensusValue = bestCluster.getKey();
    }

    public int getBestCredibility() {
        return this.bestCredibility;
    }

    public String getConsensus() {
        return this.consensusValue;
    }


}
