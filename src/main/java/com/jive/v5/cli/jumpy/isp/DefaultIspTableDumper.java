package com.jive.v5.cli.jumpy.isp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import lombok.NonNull;
import lombok.experimental.Builder;
import lombok.extern.slf4j.Slf4j;

import com.google.common.net.HostAndPort;
import com.google.common.reflect.TypeToken;
import com.jive.myco.commons.concurrent.Pnky;
import com.jive.myco.commons.concurrent.PnkyPromise;
import com.jive.myco.commons.hawtdispatch.DefaultDispatchQueueBuilder;
import com.jive.myco.isp.api.OriginatorId;
import com.jive.myco.isp.client.DefaultAsyncIspClient;
import com.jive.myco.isp.client.api.AsyncIspClient;
import com.jive.myco.isp.client.api.IspClientTableListener;
import com.jive.myco.isp.client.api.IspSerializer;
import com.jive.v5.cli.jumpy.jim.Jim;
import com.jive.v5.cli.jumpy.jim.JimInstance;
import com.jive.v5.cli.jumpy.jim.JimNetwork;
import com.jive.v5.cli.jumpy.jim.SshTunnelHandle;

/**
 * @author David Valeri
 */
@Builder
@Slf4j
public class DefaultIspTableDumper implements IspTableDumper
{
  private static final Random random = new Random();

  @NonNull
  private final Jim jim;

  private final String sshUser;

  @NonNull
  private final String tableName;

  @Override
  public List<byte[]> dumpTable(final JimInstance instance)
  {
    return doWithIspClient(
        instance,
        (i) ->
        {
          final Pnky<List<byte[]>> dumpFinishedPromise = Pnky.create();
          final IspClientTableListener<byte[]> listener = createListener(dumpFinishedPromise);

          i.listen(tableName, listener);

          return dumpFinishedPromise;
        });
  }

  @Override
  public <T> List<T> dumpTable(
      final JimInstance instance,
      final IspSerializer serializer,
      final TypeToken<T> typeToken)
  {
    final Pnky<List<T>> dumpFinishedPromise = Pnky.create();
    final IspClientTableListener<T> listener = createListener(dumpFinishedPromise);

    return doWithIspClient(
        instance,
        (i) ->
        {
          i.listen(tableName, listener, serializer, typeToken);

          return dumpFinishedPromise;
        });
  }

  private <T> T doWithIspClient(
      final JimInstance instance,
      final Function<AsyncIspClient, PnkyPromise<T>> ispClientConsumer)
  {
    final JimNetwork network = instance.getNetworks()
        .stream()
        .filter((n) -> n.getName().equals("dev") || n.getName().equals("v4compat"))
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException(
                String.format(
                    "Unable to find network on ISP Reflector [%s] to connect to. Networks: [%s].",
                    instance.getRid(),
                    instance.getNetworks())));

    try (final SshTunnelHandle openSshTunnel =
        jim.openSshTunnel(sshUser, network.getAddress(), 1111))
    {
      final DefaultAsyncIspClient ispClient = DefaultAsyncIspClient.builder()
          .dispatchQueueBuilder(DefaultDispatchQueueBuilder.getDefaultBuilder())
          .localId(OriginatorId.valueOf(random.nextLong()))
          .reflectors(Arrays.asList(HostAndPort.fromParts("127.0.0.1", openSshTunnel.getPort())))
          .build();

      return ispClient.init()
          .thenTransform((voyd) -> ispClient)
          .thenCompose(ispClientConsumer::apply)
          .alwaysCompose((r, t) ->
          {
            return ispClient.destroy()
                .<T> alwaysCompose((r2, t2) ->
                {
                  // Ignore destroy failure.
                    log.error("Error destroying ISP Client.", t2);

                  if (t != null)
                  {
                    return Pnky.immediatelyFailed(t);
                  }
                  else
                  {
                    return Pnky.immediatelyComplete(r);
                  }
                });
          })
          .get(1, TimeUnit.MINUTES);
    }
    catch (final Exception e)
    {
      throw new RuntimeException("Error with ISP Client: " + e.getMessage(), e);
    }
  }

  private <T> IspClientTableListener<T> createListener(final Pnky<List<T>> dumpFinishedPromise)
  {
    final IspClientTableListener<T> listener = new IspClientTableListener<T>()
    {
      private final List<T> datas = new LinkedList<>();

      @Override
      public void withdrawn(final long handle, final OriginatorId[] path, final T data)
      {
        // No-Op
      }

      @Override
      public void replaced(final long oldHandle, final long newHandle, final OriginatorId[] path,
          final T data)
      {
        // No-Op
      }

      @Override
      public void disconnected()
      {
        dumpFinishedPromise.reject(
            new IllegalStateException("Disconnected from ISP before completing dump."));
      }

      @Override
      public void announced(final boolean realtime, final long handle, final OriginatorId[] path,
          final T data)
      {
        log.debug("Received [{}].", data);
        datas.add(data);
      }

      @Override
      public void synced()
      {
        log.debug("Synched.");
        dumpFinishedPromise.resolve(new ArrayList<>(datas));
      }
    };
    return listener;
  }
}
