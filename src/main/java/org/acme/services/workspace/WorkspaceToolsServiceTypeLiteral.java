package org.acme.services.workspace;

import jakarta.enterprise.util.AnnotationLiteral;

public class WorkspaceToolsServiceTypeLiteral extends AnnotationLiteral<WorkspaceToolsServiceType> implements WorkspaceToolsServiceType {

    private final ExecutionMode type;

    public WorkspaceToolsServiceTypeLiteral(ExecutionMode type) {
        this.type = type;
    }

    @Override
    public ExecutionMode type() {
        return type;
    }
}
