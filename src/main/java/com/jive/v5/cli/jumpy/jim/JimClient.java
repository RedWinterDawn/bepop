package com.jive.v5.cli.jumpy.jim;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * @author Brandon Pedersen &lt;bpedersen@getjive.com&gt;
 */
@Path("api")
public interface JimClient
{
  @Path("instances")
  @GET
  ListenableFuture<List<JimInstance>> listInstances(@QueryParam("q") final String query);
}
