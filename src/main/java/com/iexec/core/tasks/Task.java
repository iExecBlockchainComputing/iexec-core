package com.iexec.core.tasks;

import org.springframework.data.annotation.Id;

public class Task {

    @Id
    private String id;

    private String commandLine;

    public Task() {}

    public Task(String commandLine) {
        this.commandLine = commandLine;
    }

    public String getId() {
        return id;
    }

    public String getCommandLine(){
        return commandLine;
    }
}
