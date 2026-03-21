package org.acme.services.workspace;

import jakarta.enterprise.util.AnnotationLiteral;

public class WorkspaceManagerTypeLiteral extends AnnotationLiteral<WorkspaceManagerType> implements WorkspaceManagerType {

    private final ExecutionMode type;

    public WorkspaceManagerTypeLiteral(ExecutionMode type) {
        this.type = type;
    }

    @Override
    public ExecutionMode type() {
        return type;
    }
}
