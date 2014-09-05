package com.jive.v5.cli.jumpy;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Cleanup;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.collect.ImmutableMap;
import com.jcraft.jsch.IdentityRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.OpenSSHConfig;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import com.jcraft.jsch.agentproxy.RemoteIdentityRepository;
import com.jcraft.jsch.agentproxy.USocketFactory;
import com.jcraft.jsch.agentproxy.connector.SSHAgentConnector;
import com.jcraft.jsch.agentproxy.usocket.NCUSocketFactory;
import com.jive.jackson.ConstructorPropertiesAnnotationIntrospector;
import com.jive.v5.cli.jumpy.jim.JimClient;
import com.jive.v5.cli.jumpy.jim.JimInstance;
import com.jive.v5.cli.jumpy.jim.JimNetwork;
import com.jive.v5.commons.rest.client.RestClient;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentChoice;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class Main implements UserInfo
{

  private final static String BASTION = "bastion";

  private static final Map<String, Formatter> FORMATTERS = ImmutableMap
      .<String, Formatter> builder()
      .put("table", new TableFormatter())
      .put("basic", new BasicFormatter())
      .put("expanded", new ExpandedFormatter())
      .build();

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
                String.format("Command version %s\n", Main.class.getPackage()
                    .getImplementationVersion()));

    argp.addArgument("--version")
        .action(Arguments.version())
        .help("Show version number and exit");

    argp.addArgument("-s", "--site")
        .dest("site")
        .setDefault("pvu-1a")
        .metavar("site")
        .help("Site to connect to jumpy");

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
        .setDefault(FORMATTERS.get("table"))
        .type((parser, arg1, value) ->
        {
          return FORMATTERS.get(value);
        })
        .help("Type of formatting for the output");

    final Namespace ns;

    try
    {
      ns = argp.parseArgs(arg);
    }
    catch (final ArgumentParserException e)
    {
      argp.handleError(e);
      System.exit(1);
      return;
    }

    final ObjectMapper mapper = new ObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(new JodaModule());
    ConstructorPropertiesAnnotationIntrospector.install(mapper);

    try (final CloseableHttpAsyncClient asyncClient = HttpAsyncClients.createDefault())
    {
      asyncClient.start();

      final RestClient restClient = new RestClient(asyncClient, mapper);
      final JimClient jimClient =
          restClient.bind(
              "http://jim.devops.jive.com:8080/",
              JimClient.class,
              (request) -> request.addHeader("Authorization",
                  "Token " + System.getenv("JIM_APIKEY")));

      final List<JimInstance> instances = jimClient.listInstances("nurse").get();

      final String site = ns.getString("site");

      final JimInstance nurseServer = instances.stream()
          .filter((instance) -> site.equals(instance.getSite()))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException(
              String.format("Unable to find nurse server to connect to in site [%s]", site)));

      final JimNetwork devNetwork = nurseServer.getNetworks()
          .stream()
          .filter((network) -> network.getName().equals("dev")
              || network.getName().equals("v4compat"))
          .findFirst()
          .orElseThrow(
              () -> new IllegalStateException(String.format(
                  "Unable to find network on nurse server to connect to. Networks: [%s]",
                  nurseServer.getNetworks())));

      final Path home = Paths.get(System.getProperty("user.home"), ".ssh");

      final JSch jsch = new JSch();

      if (SSHAgentConnector.isConnectorAvailable())
      {
        // note: using the nc version to avoid an annoying window popping up every time!
        final USocketFactory usf = new NCUSocketFactory();
        final SSHAgentConnector con = new SSHAgentConnector(usf);
        final IdentityRepository irepo = new RemoteIdentityRepository(con);
        jsch.setIdentityRepository(irepo);
      }

      jsch.setKnownHosts(home.resolve("known_hosts").toAbsolutePath().toString());
      jsch.setConfigRepository(OpenSSHConfig.parseFile(home.resolve("config").toAbsolutePath()
          .toString()));

      @Cleanup("disconnect")
      final Session session = jsch.getSession(ns.getString("user"), BASTION, 22);

      session.setConfig("PreferredAuthentications", "publickey");
      session.setConfig("StrictHostKeyChecking", "no");

      // username and password will be given via UserInfo interface.

      session.setUserInfo(new Main());

      session.connect(5000);

      final int assignedPort =
          session.setPortForwardingL(0, devNetwork.getAddress(), 8080);

      // now make a HTTP request to this port.

      try (CloseableHttpClient client = HttpClientBuilder.create().build())
      {

        final HttpGet req = new HttpGet(String.format("http://127.0.0.1:%d/jumpy", assignedPort));

        req.addHeader("Accept", "application/json");

        try (CloseableHttpResponse res = client.execute(req))
        {

          final List<Service> services = mapper.readValue(res.getEntity().getContent(),
              CollectionType.construct(List.class, SimpleType.construct(Service.class)));

          Formatter formatter = ns.get("format");
          System.out.println(formatter.format(services));

        }

      }
    }
    catch (final Exception e)
    {
      System.out.println(e);
    }

  }

  @Override
  public String getPassphrase()
  {
    return "";
  }

  @Override
  public String getPassword()
  {
    return "";
  }

  @Override
  public boolean promptPassphrase(final String id)
  {
    return true;
  }

  @Override
  public boolean promptPassword(final String arg0)
  {
    return true;
  }

  @Override
  public boolean promptYesNo(final String arg0)
  {
    return true;
  }

  @Override
  public void showMessage(final String arg0)
  {
    System.err.println("MSG" + arg0);
  }

}
