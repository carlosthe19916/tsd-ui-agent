package org.acme.services.changerequest.gitlab;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Encoded;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/v4")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Encoded
public interface GitLabApi {

    @GET
    @Path("/projects/{id}")
    ProjectResponse getProject(@PathParam("id") String encodedPath);

    @POST
    @Path("/projects/{id}/merge_requests")
    MergeRequestResponse createMergeRequest(@PathParam("id") String encodedPath,
                                             CreateMergeRequest body);

    @GET
    @Path("/projects/{id}/merge_requests")
    List<MergeRequestResponse> listMergeRequests(@PathParam("id") String encodedPath,
                                                  @QueryParam("source_branch") String sourceBranch,
                                                  @QueryParam("target_branch") String targetBranch,
                                                  @QueryParam("state") String state);
}
