package org.acme.services.changerequest;

import org.acme.models.jpa.entity.GitVendorType;

public interface ChangeRequestProvider {
    boolean supports(GitVendorType vendorType);
    String buildAuthenticatedPushUrl(String gitUrl, String token);
    ChangeRequestResult createChangeRequest(ChangeRequestParams params) throws Exception;
    ChangeRequestResult findExistingChangeRequest(ChangeRequestParams params) throws Exception;
}
