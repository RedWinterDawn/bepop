package com.jive.v5.cli.jumpy.jim;

/**
 * @author David Valeri
 */
public interface SshTunnelHandle extends AutoCloseable
{
  int getPort();
}
