package com.iexec.core;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.concurrent.atomic.AtomicLong;

@Controller
public class DummyController {

    private static final String TEMPLATE = "Hello, %s!";
    private final AtomicLong counter = new AtomicLong();

    @GetMapping("/hello")
    @ResponseBody
    public String sayHello(@RequestParam(name="name", required=false, defaultValue="Stranger") String name) {
        return counter.incrementAndGet() + "_" + String.format(TEMPLATE, name);
    }
}