package com.jive.bebop.jumpy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentChoice;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.jive.bebop.jumpy.formatter.BasicFormatter;
import com.jive.bebop.jumpy.formatter.ExpandedFormatter;
import com.jive.bebop.jumpy.formatter.Formatter;
import com.jive.bebop.jumpy.formatter.TableFormatter;
import com.jive.myco.bebop.utils.isp.DefaultIspTableDumper;
import com.jive.myco.bebop.utils.isp.IspTableDumper;
import com.jive.myco.bebop.utils.jim.DefaultJim;
import com.jive.myco.bebop.utils.jim.Jim;
import com.jive.myco.bebop.utils.jim.JimInstance;
import com.jive.myco.isp.client.serialization.jackson.JacksonJsonIspSerializer;
import com.jive.v5.jumpy.model.JumpyRecord;

/**
 * @author David Valeri
 */
public class Main
{
  private static final Map<String, Formatter> FORMATTERS = ImmutableMap
      .<String, Formatter> builder()
      .put("table", new TableFormatter())
      .put("basic", new BasicFormatter())
      .put("expanded", new ExpandedFormatter())
      .build();

  private static String site;

  public static void main(final String[] arg)
  {
    final ArgumentParser argp =
        ArgumentParsers
            .newArgumentParser("jumpy")
            .defaultHelp(true)
            .usage("jumpy [args]")
            .version(Main.class.getPackage().getImplementationVersion())
            .description("Show jumpy service states.")
            .epilog(
                String.format(
                    "Command version %s\n",
                    Main.class.getPackage().getImplementationVersion()));

    argp.addArgument("--version")
        .action(Arguments.version())
        .help("Show version number and exit");

    argp.addArgument("-s", "--site")
        .dest("site")
        .setDefault("pvu-1a")
        .metavar("site")
        .help("Site to connect to jumpy");

    argp.addArgument("-l", "--local")
        .action(Arguments.storeTrue())
        .help("Only show services registered in the specified site");

    argp.addArgument("-u")
        .dest("user")
        .metavar("ssh-user")
        .help("Bastion username");

    argp.addArgument("-f", "--format")
        .choices(new ArgumentChoice()
        {
          @Override
          public boolean contains(final Object val)
          {
            return FORMATTERS.values().contains(val);
          }

          @Override
          public String textualFormat()
          {
            return FORMATTERS.keySet()
                .stream()
                .collect(Collectors.joining(",", "{", "}"));
          }
        })
        .setDefault(FORMATTERS.get("expanded"))
        .type((parser, arg1, value) -> FORMATTERS.get(value))
        .help("Type of formatting for the output");

    final Namespace ns;

    try (Jim jim = new DefaultJim())
    {
      ns = argp.parseArgs(arg);
      site = ns.getString("site");

      jim.init();

      final List<JimInstance> instances = jim.listInstances("reflector");
      final String site = ns.getString("site");

      final JimInstance instance = instances.stream()
          .filter((i) -> site.equals(i.getSite()))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException(
              String.format("Unable to find ISP Reflector to connect to in site [%s]", site)));

      final IspTableDumper jumpyTableDumper = DefaultIspTableDumper.builder()
          .jim(jim)
          .sshUser(ns.getString("user"))
          .tableName("isp.jumpy.v1")
          .build();

      final Formatter formatter = ns.get("format");

      List<JumpyRecord> jumpyRecords = jumpyTableDumper
          .dumpTable(
              instance,
              JacksonJsonIspSerializer.createDefault(),
              TypeToken.of(JumpyRecord.class));

      if (ns.getBoolean("local"))
      {
        jumpyRecords = jumpyRecords.stream()
            .filter(Main::checkSite)
            .collect(Collectors.toList());
      }

      System.out.println(formatter.format(jumpyRecords));
    }
    catch (final ArgumentParserException e)
    {
      argp.handleError(e);
      System.exit(1);
      return;
    }
    catch (final Exception e)
    {
      System.err.println(e);
    }
  }

  private static boolean checkSite(JumpyRecord record)
  {
    return record.getProperties().getOrDefault("jazz.rt.coordinates", "").contains(site);
  }
}
