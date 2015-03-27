package com.jive.v5.cli.jumpy.jim;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import com.jive.myco.commons.concurrent.PnkyPromise;

/**
 * @author Brandon Pedersen &lt;bpedersen@getjive.com&gt;
 */
@Path("api")
public interface JimClient
{
  @Path("instances")
  @GET
  PnkyPromise<List<JimInstance>> listInstances(@QueryParam("q") final String query);

  @Path("instances/{rid}")
  @GET
  PnkyPromise<JimInstance> getInstance(@QueryParam("rid") final String resourceId);
}
