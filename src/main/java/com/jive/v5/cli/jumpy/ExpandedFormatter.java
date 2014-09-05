package com.jive.v5.cli.jumpy;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Brandon Pedersen &lt;bpedersen@getjive.com&gt;
 */
public class ExpandedFormatter implements Formatter
{

  @Override
  public String format(final List<Service> services)
  {
    return services
        .parallelStream()
        .<String> map(
            (service) ->
            {
              return service
                  .getProperties()
                  .entrySet()
                  .stream()
                  .sorted(Comparator.comparing(Map.Entry::getKey))
                  .<String> map((entry) ->
                      String.format("%35s %s", entry.getKey(), entry.getValue()))
                  .collect(Collectors.joining(System.lineSeparator(),
                      service.getName() + System.lineSeparator(), ""));
            })
        .sorted()
        .collect(Collectors.joining(System.lineSeparator()));
  }
}
