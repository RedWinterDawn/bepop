package com.jive.v5.cli.jumpy;

import java.util.List;

/**
 * @author Brandon Pedersen &lt;bpedersen@getjive.com&gt;
 */
public interface Formatter
{
  String format(List<Service> services);
}
