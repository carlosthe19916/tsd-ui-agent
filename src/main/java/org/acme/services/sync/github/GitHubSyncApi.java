package org.acme.services.sync.github;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;

@Produces(MediaType.APPLICATION_JSON)
@ClientHeaderParam(name = "Accept", value = "application/vnd.github+json")
public interface GitHubSyncApi {

    @GET
    @Path("/repos/{owner}/{repo}")
    JsonNode getRepo(@PathParam("owner") String owner,
                     @PathParam("repo") String repo);

    @GET
    @Path("/repos/{owner}/{repo}/issues")
    GitHubIssue[] listIssues(@PathParam("owner") String owner,
                             @PathParam("repo") String repo,
                             @QueryParam("state") String state,
                             @QueryParam("per_page") int perPage,
                             @QueryParam("page") int page);

    @GET
    @Path("/search/issues")
    GitHubSearchResult searchIssues(@QueryParam("q") String query,
                                   @QueryParam("per_page") int perPage);

    @GET
    @Path("/repos/{owner}/{repo}/issues/{number}/comments")
    JsonNode listComments(@PathParam("owner") String owner,
                          @PathParam("repo") String repo,
                          @PathParam("number") int number,
                          @QueryParam("per_page") int perPage);

    @GET
    @Path("/repos/{owner}/{repo}/issues/{number}")
    JsonNode getIssue(@PathParam("owner") String owner,
                      @PathParam("repo") String repo,
                      @PathParam("number") int number);
}
