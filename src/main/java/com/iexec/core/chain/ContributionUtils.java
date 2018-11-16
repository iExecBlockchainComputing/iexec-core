package com.iexec.core.chain;

import com.iexec.core.replicate.Replicate;
import com.iexec.core.task.Task;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ContributionUtils {

    public static LinkedHashMap<String, Integer> sortClustersByCredibility(Map<String, Integer> credibilityMap) {
        return credibilityMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public static void main(String[] args) {

        Map<String, Integer> map = new HashMap<>();
        map.put("0x2", 3);
        map.put("0x4", 1);
        map.put("0x1", 1);
        map.put("0x3", 5);

        map = sortClustersByCredibility(map);

        for (String hash: map.keySet()){
            System.out.println(hash + " " + map.get(hash));
        }
    }

    public static Map<String, Integer> getHash2CredibilityClusters(Task task) {
        Map<String, Integer> resultHashToC = new HashMap<>();
        for (Replicate replicate: task.getReplicates()){
            String hash = replicate.getResultHash();
            int c = replicate.getCredibility();

            if (!resultHashToC.containsKey(hash)){
                resultHashToC.put(hash, c);
            } else {
                Integer totalC = resultHashToC.get(hash);
                totalC += c;
                resultHashToC.replace(hash, totalC);
            }
        }
        return resultHashToC;
    }
}
