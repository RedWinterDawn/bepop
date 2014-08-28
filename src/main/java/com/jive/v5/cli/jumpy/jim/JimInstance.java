package com.jive.v5.cli.jumpy.jim;

import java.util.List;

import lombok.Value;

/**
 * @author Brandon Pedersen &lt;bpedersen@getjive.com&gt;
 */
@Value
public class JimInstance
{
  private final String service;
  private final int instance;
  private final String site;
  private final int bm;
  private final List<JimNetwork> networks;
  private final String branch;
}
