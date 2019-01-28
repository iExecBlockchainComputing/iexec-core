package com.iexec.core.configuration;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * This simple service will generate a random session id when the scheduler is started, it will be send to workers when
 * they ping the scheduler. If they see that the session id has changed, it means that the scheduler has restarted.
 */
@Service
public class SessionService {

    private String sessionId;

    @PostConstruct
    private void init(){
        sessionId = RandomStringUtils.randomAlphanumeric(10);
    }

    public String getSessionId(){
        return sessionId;
    }

}
