package com.jive.v5.cli.jumpy;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Brandon Pedersen &lt;bpedersen@getjive.com&gt;
 */
public class BasicFormatter implements Formatter
{

  @Override
  public String format(final List<Service> services)
  {
    return services.parallelStream()
        .<String> map((service) ->
            String.format("%35s %s", service.getName(), service.getProperties()))
        .collect(Collectors.joining(System.lineSeparator()));
  }
}
