package com.jive.v5.cli.jumpy.formatter;

import java.util.List;
import java.util.stream.Collectors;

import com.jive.v5.jumpy.model.JumpyRecord;

/**
 * @author Brandon Pedersen &lt;bpedersen@getjive.com&gt;
 */
public class BasicFormatter implements Formatter
{

  @Override
  public String format(final List<JumpyRecord> jumpyRecords)
  {
    return jumpyRecords.parallelStream()
        .<String> map((service) ->
            String.format("%35s %s", service.getName(), service.getProperties()))
        .collect(Collectors.joining(System.lineSeparator()));
  }
}
