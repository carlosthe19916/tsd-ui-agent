package org.acme.services.codeagent;

import jakarta.enterprise.util.AnnotationLiteral;

public class CodingAgentQualifierLiteral extends AnnotationLiteral<CodingAgentQualifier> implements CodingAgentQualifier {

    private final CodingAgentType value;

    public CodingAgentQualifierLiteral(CodingAgentType value) {
        this.value = value;
    }

    @Override
    public CodingAgentType value() {
        return value;
    }
}
