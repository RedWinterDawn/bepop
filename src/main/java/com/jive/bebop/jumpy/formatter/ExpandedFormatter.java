package com.jive.bebop.jumpy.formatter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.jive.v5.jumpy.model.JumpyRecord;

/**
 * @author Brandon Pedersen &lt;bpedersen@getjive.com&gt;
 */
public class ExpandedFormatter implements Formatter
{
  @Override
  public String format(final List<JumpyRecord> jumpyRecords)
  {
    return jumpyRecords
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
