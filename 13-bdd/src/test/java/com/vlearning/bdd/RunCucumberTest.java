package com.vlearning.bdd;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectPackages("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.vlearning.bdd.steps")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, summary")
/**
 * The single entry point that makes {@code mvn test} run Gherkin features next to plain
 * JUnit tests. Surefire discovers this class by its {@code *Test} name, the JUnit Platform
 * Suite engine runs it, and it delegates to the Cucumber engine for every {@code .feature}
 * file under {@code src/test/resources/features}.
 */
public class RunCucumberTest {
}
