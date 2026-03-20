package org.acme.services.changerequest.github;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;

import java.util.List;

@Path("/repos/{owner}/{repo}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface GitHubApi {

    @POST
    @Path("/pulls")
    @ClientHeaderParam(name = "Accept", value = "application/vnd.github+json")
    PullRequestResponse createPullRequest(@PathParam("owner") String owner,
                                           @PathParam("repo") String repo,
                                           CreatePullRequest body);

    @GET
    @Path("/pulls")
    @ClientHeaderParam(name = "Accept", value = "application/vnd.github+json")
    List<PullRequestResponse> listPullRequests(@PathParam("owner") String owner,
                                                @PathParam("repo") String repo,
                                                @QueryParam("head") String head,
                                                @QueryParam("state") String state);
}
