package com.iexec.core.chain;

import com.iexec.core.replicate.Replicate;
import com.iexec.core.task.Task;

import java.util.Date;
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

    public static int scoreToCredibility(int score) {
        int credibility = score;
        /*
         *  should be :   c(s)=1-0.2/max(s,1)
         *  considering   :   c(s)= s
         */
        return credibility;
    }

    public static int trustToCredibility(int trust) {
        int credibility = trust;
        /*
         *  should be :   c(s)=1-1/max(s,1)
         *  considering   :   c(s)= s
         */
        return credibility;
    }

    public static long now() {
        return new Date().getTime() / 1000;
    }

    public static void main(String[] args) {
        //TODO move to tests
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
}
