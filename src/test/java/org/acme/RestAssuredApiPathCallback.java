package org.acme;

import io.quarkus.test.junit.callback.QuarkusTestBeforeTestExecutionCallback;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;
import io.restassured.RestAssured;

public class RestAssuredApiPathCallback implements QuarkusTestBeforeTestExecutionCallback {

    @Override
    public void beforeTestExecution(QuarkusTestMethodContext context) {
        RestAssured.basePath = "/api";
    }
}
