package com.iexec.core.chain;

public class ContributionUtils {

    private ContributionUtils() {
        throw new UnsupportedOperationException();
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

}
