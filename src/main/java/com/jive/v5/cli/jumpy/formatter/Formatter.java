package com.jive.v5.cli.jumpy.formatter;

import java.util.List;

import com.jive.v5.jumpy.model.JumpyRecord;

/**
 * @author Brandon Pedersen &lt;bpedersen@getjive.com&gt;
 */
public interface Formatter
{
  String format(final List<JumpyRecord> jumpyRecords);
}
