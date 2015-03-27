package com.jive.v5.cli.jumpy.isp;

import java.util.List;

import com.google.common.reflect.TypeToken;
import com.jive.myco.isp.client.api.IspSerializer;
import com.jive.v5.cli.jumpy.jim.JimInstance;

/**
 * @author David Valeri
 */
public interface IspTableDumper
{
  List<byte[]> dumpTable(final JimInstance instance);

  <T> List<T> dumpTable(
      final JimInstance instance,
      final IspSerializer serializer,
      final TypeToken<T> typeToken);

}
