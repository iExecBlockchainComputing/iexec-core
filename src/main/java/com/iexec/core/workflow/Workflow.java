package com.iexec.core.workflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;


@SuppressWarnings("unchecked")
public class Workflow<T> {

    private HashMap<T, List<T>> possibleTransitions;

    public Workflow() {
        possibleTransitions = new HashMap<T, List<T>>();
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
}
