package org.acme.services.sync.jira;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Produces(MediaType.APPLICATION_JSON)
public interface JiraSyncApi {

    @GET
    @Path("/rest/api/3/myself")
    JsonNode myself();

    @GET
    @Path("/rest/api/3/search/jql")
    JiraPageResponse searchJql(@QueryParam("jql") String jql,
                               @QueryParam("maxResults") int maxResults,
                               @QueryParam("fields") String fields,
                               @QueryParam("nextPageToken") String nextPageToken);

    @GET
    @Path("/rest/api/3/issue/{issueKey}/comment")
    JsonNode getComments(@PathParam("issueKey") String issueKey);

    @GET
    @Path("/rest/api/3/issue/{issueKey}")
    JsonNode getIssue(@PathParam("issueKey") String issueKey,
                      @QueryParam("fields") String fields);
}
