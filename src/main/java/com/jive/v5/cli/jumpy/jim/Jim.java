package com.jive.v5.cli.jumpy.jim;

import java.util.List;

/**
 * @author David Valeri
 */
public interface Jim extends AutoCloseable
{
  void init();

  List<JimInstance> listInstances(final String query);

  JimInstance getInstance(final String resourceId);

  SshTunnelHandle openSshTunnel(final String user, final String host, final int port);
}
