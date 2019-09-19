package com.iexec.core;

import com.iexec.core.chain.IexecHubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.elasticsearch.jest.JestAutoConfiguration;


@SpringBootApplication
public class Application implements CommandLineRunner {

    @Autowired
    private IexecHubService iexecHubService;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        if (!iexecHubService.hasEnoughGas()) {
            System.exit(0);
        }
    }
}
