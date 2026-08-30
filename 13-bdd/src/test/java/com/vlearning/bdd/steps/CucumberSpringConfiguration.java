package com.vlearning.bdd.steps;

import com.vlearning.bdd.BddApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@CucumberContextConfiguration
@SpringBootTest(classes = BddApplication.class)
public class CucumberSpringConfiguration {
}
