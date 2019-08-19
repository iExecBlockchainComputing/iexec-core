package com.iexec.core.workflow;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@SuppressWarnings("unchecked")
public class Workflow<T> {

    private Map<T, List<T>> possibleTransitions;

    public Workflow() {
        possibleTransitions = new LinkedHashMap<T, List<T>>();
    }

    boolean addTransition(T from, T to) {
        if (possibleTransitions.containsKey(from)) {
            return possibleTransitions.get(from).add(to);
        }

        return possibleTransitions.put(from, toList(to)) != null;
    }

    boolean addTransition(T from, List<T> to) {
        if(possibleTransitions.containsKey(from)) {
            return possibleTransitions.get(from).addAll(to);
        }

        return possibleTransitions.put(from, to) != null;
    }

    void addTransition(List<T> froms, T to) {
        for (T from: froms){
            addTransition(from, to);
        }
    }

    void addTransitionFromAllStatusesTo(T status) {
        for (T key : possibleTransitions.keySet()) {
            addTransition(key, status);
        }
    }

    void addTransitionFromStatusToAllStatuses(T status) {
        List<T> to = new ArrayList<T>();
        to.addAll(possibleTransitions.keySet());
        addTransition(status, to);
    }

    public boolean isValidTransition(T from, T to){
        return possibleTransitions.containsKey(from)
            ? possibleTransitions.get(from).contains(to)
            : false;
    }

    List<T> toList(T... statuses) {
        return new ArrayList<T>(Arrays.asList(statuses));
    }

    Map<T, List<T>> getTransitions() {
        return possibleTransitions;
    }

    void saveWorkflowAsJsonFile(String filePath, Object workflowObject) {
        ObjectWriter ow = new ObjectMapper().writerWithDefaultPrettyPrinter();

        try (PrintWriter out = new PrintWriter(filePath)) {
            String transitionsJson = ow.writeValueAsString(workflowObject);
            out.println(transitionsJson);
        } catch (Exception e) {
            log.error("Could not save object as json files [filePath:{}, object:{}]",
                    filePath, workflowObject);
            e.printStackTrace();
        }
    }
}
