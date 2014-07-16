package com.jive.v5.cli.jumpy;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

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
  private final static String JUMPY_IP = "10.117.255.100";

  public static void main(String[] arg)
  {

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

      Session session = jsch.getSession(null, BASTION, 22);

      session.setConfig("PreferredAuthentications", "publickey");
      session.setConfig("StrictHostKeyChecking", "no");

      // username and password will be given via UserInfo interface.

      session.setUserInfo(new Main());

      session.connect(5000);

      int assinged_port = session.setPortForwardingL(0, JUMPY_IP, 8080);

      // now make a HTTP request to this port.

      try (CloseableHttpClient client = HttpClientBuilder.create().build())
      {

        HttpGet req = new HttpGet(String.format("http://127.0.0.1:%d/jumpy", assinged_port));

        req.addHeader("Accept", "application/json");

        try (CloseableHttpResponse res = client.execute(req))
        {

          ObjectMapper mapper = new ObjectMapper();

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
