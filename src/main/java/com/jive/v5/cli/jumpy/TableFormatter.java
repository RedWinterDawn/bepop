package com.jive.v5.cli.jumpy;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.bethecoder.ascii_table.ASCIITable;
import com.bethecoder.ascii_table.ASCIITableHeader;
import com.bethecoder.ascii_table.spec.IASCIITableAware;

/**
 * @author Brandon Pedersen &lt;bpedersen@getjive.com&gt;
 */
public class TableFormatter implements Formatter
{

  @Override
  public String format(final List<Service> services)
  {
    final List<ASCIITableHeader> tableHeaders = services
        .parallelStream()
        .map(Service::getProperties)
        .flatMap((props) -> props.keySet().stream())
        .distinct()
        .sorted()
        .map((key) -> new ASCIITableHeader(key, ASCIITable.ALIGN_LEFT))
        .collect(Collectors.toList());

    tableHeaders.add(0, new ASCIITableHeader("name", ASCIITable.ALIGN_LEFT));

    final List<List<Object>> data = services
        .parallelStream()
        .map((service) ->
        {
          return tableHeaders
              .stream()
              .map((header) ->
              {
                if ("name".equals(header.getHeaderName()))
                {
                  return service.getName();
                }
                else
                {
                  return service.getProperties().getOrDefault(header.getHeaderName(), "");
                }
              })
              .collect(Collectors.toList());
        })
        .sorted(Comparator.comparing((list) -> list.get(0), Comparator.naturalOrder()))
        .map((list) -> list.stream().collect(Collectors.<Object> toList()))
        .collect(Collectors.toList());

    return ASCIITable.getInstance().getTable(new IASCIITableAware()
    {
      @Override
      public List<ASCIITableHeader> getHeaders()
      {
        return tableHeaders;
      }

      @Override
      public List<List<Object>> getData()
      {
        return data;
      }

      @Override
      public String formatData(final ASCIITableHeader header, final int row, final int col,
          final Object data)
      {
        return null;
      }
    });
  }
}
