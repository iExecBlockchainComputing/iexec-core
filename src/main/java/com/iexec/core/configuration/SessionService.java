package com.iexec.core.configuration;

import org.apache.commons.lang3.RandomStringUtils;

/**
 * This simple service will generate a random session id when the scheduler is started, it will be send to workers when
 * they ping the scheduler. If they see that the session id has changed, it means that the scheduler has restarted.
 */
public class SessionService {

    private static final String sessionId = RandomStringUtils.randomAlphanumeric(10);

    public static String getSessionId(){
        return sessionId;
    }
}
