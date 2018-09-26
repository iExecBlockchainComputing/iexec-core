package com.iexec.core.tasks;

import org.springframework.data.annotation.Id;

import java.util.*;

public class Task {

    @Id
    private String id;

    private List<TaskStatusChange> dateStatusList;

    private String commandLine;

    public Task() {}

    public Task(String commandLine) {
        this.commandLine = commandLine;
        this.dateStatusList = new ArrayList<>();
        this.dateStatusList.add(new TaskStatusChange(new Date(), TaskStatus.CREATED));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<TaskStatusChange> getDateStatusList() {
        return dateStatusList;
    }

    public void setDateStatusList(List<TaskStatusChange> dateStatusList) {
        this.dateStatusList = dateStatusList;
    }

    public String getCommandLine() {
        return commandLine;
    }

    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }
}
