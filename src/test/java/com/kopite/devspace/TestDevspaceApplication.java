package com.kopite.devspace;

import org.springframework.boot.SpringApplication;

public class TestDevspaceApplication {

    public static void main(String[] args) {
        SpringApplication.from(DevspaceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
