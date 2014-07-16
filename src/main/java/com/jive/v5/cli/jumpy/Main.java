package com.jive.v5.cli.jumpy;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.SimpleType;
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

public class Main implements UserInfo
{

  private final static String BASTION = "bastion";

  public static void main(String[] arg)
  {

    Map<String, String> sites = new HashMap<>();

    sites.put("pvu-1a", "10.117.255.100");
    sites.put("dfw-1a", "10.118.255.100");

    ArgumentParser argp = ArgumentParsers.newArgumentParser("jumpy")
        .defaultHelp(true)
        .usage("jumpy [args]")
        .version(Main.class.getPackage().getImplementationVersion())
        .description("Show jumpy service states.")
        .epilog(String.format("Command version %s\n", Main.class.getPackage().getImplementationVersion()));

    argp.addArgument("--version")
        .action(Arguments.version())
        .help("Show version number and exit");

    argp.addArgument("-s", "--site")
        .dest("site")
        .setDefault("pvu-1a")
        .choices(sites.keySet())
        .metavar("site")
        .help("Site to connect to jumpy");

    argp.addArgument("-u")
        .dest("user")
        .metavar("ssh-user")
        .help("Bastion username");

    Namespace ns;

    try
    {
      ns = argp.parseArgs(arg);
    }
    catch (ArgumentParserException e)
    {
      argp.handleError(e);
      System.exit(1);
      return;
    }

    try
    {

      Path home = Paths.get(System.getProperty("user.home"), ".ssh");

      JSch jsch = new JSch();

      if (SSHAgentConnector.isConnectorAvailable())
      {
        // note: using the nc version to avoid an annoying window popping up every time!
        USocketFactory usf = new NCUSocketFactory();
        SSHAgentConnector con = new SSHAgentConnector(usf);
        IdentityRepository irepo = new RemoteIdentityRepository(con);
        jsch.setIdentityRepository(irepo);
      }

      jsch.setKnownHosts(home.resolve("known_hosts").toAbsolutePath().toString());
      jsch.setConfigRepository(OpenSSHConfig.parseFile(home.resolve("config").toAbsolutePath().toString()));

      Session session = jsch.getSession(ns.getString("user"), BASTION, 22);

      session.setConfig("PreferredAuthentications", "publickey");
      session.setConfig("StrictHostKeyChecking", "no");

      // username and password will be given via UserInfo interface.

      session.setUserInfo(new Main());

      session.connect(5000);

      int assinged_port = session.setPortForwardingL(0, sites.get(ns.getString("site")), 8080);

      // now make a HTTP request to this port.

      try (CloseableHttpClient client = HttpClientBuilder.create().build())
      {

        HttpGet req = new HttpGet(String.format("http://127.0.0.1:%d/jumpy", assinged_port));

        req.addHeader("Accept", "application/json");

        try (CloseableHttpResponse res = client.execute(req))
        {

          ObjectMapper mapper = new ObjectMapper();
          
          mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

          ConstructorPropertiesAnnotationIntrospector.install(mapper);

          List<Service> services = mapper.readValue(res.getEntity().getContent(),
              CollectionType.construct(List.class, SimpleType.construct(Service.class)));

          services.forEach((e) -> System.out.println(String.format("%35s %s", e.getName(), e.getProperties())));

        }

      }

      session.disconnect();

    }
    catch (Exception e)
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
  public boolean promptPassphrase(String id)
  {
    return true;
  }

  @Override
  public boolean promptPassword(String arg0)
  {
    return true;
  }

  @Override
  public boolean promptYesNo(String arg0)
  {
    return true;
  }

  @Override
  public void showMessage(String arg0)
  {
    System.err.println("MSG" + arg0);
  }

}
