package com.iexec.core;

import com.iexec.common.dummy.DummyClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application implements CommandLineRunner {


	@Autowired
	private DummyRepository repository;

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
        repository.deleteAll();

        // save a couple of customers
        repository.save(new DummyClass("Alice"));
        repository.save(new DummyClass("Bob"));


        // fetch all customers
        System.out.println("Customers found with findAll():");
        System.out.println("-------------------------------");
        for (DummyClass dummy: repository.findAll()) {
            System.out.println(dummy);
        }
        System.out.println();

        // fetch an individual customer
        System.out.println("Customer found with findByFirstName('Alice'):");
        System.out.println("--------------------------------");
        System.out.println(repository.findByDummyField("Alice"));

        System.out.println("Customers found with findByLastName('Smith'):");
        System.out.println("--------------------------------");
        System.out.println(repository.findByDummyField("Smith"));
    }
}
