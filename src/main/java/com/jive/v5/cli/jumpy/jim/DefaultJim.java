package com.jive.v5.cli.jumpy.jim;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
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
import com.jive.myco.jazz.api.rest.client.RestClientFactory;
import com.jive.myco.jazz.rest.client.DefaultRestClientFactory;
import com.jive.myco.jazz.rest.client.JacksonJsonRestClientSerializer;

/**
 * @author David Valeri
 */
public class DefaultJim implements Jim
{
  private final static String BASTION = "bastion";

  private final List<SshTunnelHandle> sshTunnelHandles = new LinkedList<>();

  private CloseableHttpAsyncClient asyncClient;
  private JimClient jimClient;

  @Override
  public void init()
  {
    final ObjectMapper mapper = new ObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(new JodaModule());
    ConstructorPropertiesAnnotationIntrospector.install(mapper);


    asyncClient = HttpAsyncClients.createDefault();

    asyncClient.start();

    final String apiKey = System.getenv("JIM_APIKEY");

    if (apiKey == null || apiKey.isEmpty())
    {
      throw new RuntimeException("No JIM API key found.");
    }

    final RestClientFactory rcf = new DefaultRestClientFactory(asyncClient);

    jimClient = rcf.bind(JimClient.class)
        .addRestClientSerializer(new JacksonJsonRestClientSerializer(mapper))
        .headerDecorator((headers) ->
        {
          headers.put("Authorization", Arrays.asList("Token " + apiKey));
        })
        .url("http://jim.devops.jive.com:8080/")
        .build();
  }

  @Override
  public void close() throws Exception
  {
    if (asyncClient != null)
    {
      try
      {
        asyncClient.close();
      }
      catch (final IOException e)
      {
        // Ignore
      }
    }

    sshTunnelHandles.forEach((h) ->
    {
      try
      {
        h.close();
      }
      catch (final Exception e)
      {
        // Ignore
      }
    });
  }

  @Override
  public List<JimInstance> listInstances(final String query)
  {
    try
    {
      return jimClient.listInstances(query).get(30, TimeUnit.SECONDS);
    }
    catch (final Exception e)
    {
      throw new RuntimeException("Error querying JIM API: " + e.getMessage(), e);
    }
  }

  @Override
  public JimInstance getInstance(final String resourceId)
  {
    try
    {
      return jimClient.getInstance(resourceId).get(30, TimeUnit.SECONDS);
    }
    catch (final Exception e)
    {
      throw new RuntimeException("Error querying JIM API: " + e.getMessage(), e);
    }
  }

  @Override
  public SshTunnelHandle openSshTunnel(final String user, final String host, final int port)
  {
    final Path home = Paths.get(System.getProperty("user.home"), ".ssh");

    final JSch jsch = new JSch();

    try
    {
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

      final Session session = jsch.getSession(user, BASTION, 22);

      session.setConfig("PreferredAuthentications", "publickey");
      session.setConfig("StrictHostKeyChecking", "no");

      // username and password will be given via UserInfo interface.
      session.setUserInfo(new InternalUserInfo());

      session.connect(5000);

      final int assignedPort =
          session.setPortForwardingL(0, host, port);

      final SshTunnelHandle sshTunnelHandle = new SshTunnelHandle()
      {

        @Override
        public int getPort()
        {
          return assignedPort;
        }

        @Override
        public void close()
        {
          session.disconnect();
        }
      };

      sshTunnelHandles.add(sshTunnelHandle);

      return sshTunnelHandle;
    }
    catch (final Exception e)
    {
      throw new RuntimeException("Error establishing tunnel: " + e.getMessage(), e);
    }
  }

  private static final class InternalUserInfo implements UserInfo
  {
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
}
