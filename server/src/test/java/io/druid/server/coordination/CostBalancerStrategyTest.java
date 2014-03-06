package io.druid.server.coordination;

import com.google.api.client.util.Lists;
import com.google.common.collect.Maps;
import io.druid.client.DruidServer;
import io.druid.server.coordinator.BalancerStrategy;
import io.druid.server.coordinator.CostBalancerStrategy;
import io.druid.server.coordinator.LoadQueuePeonTester;
import io.druid.server.coordinator.ServerHolder;
import io.druid.timeline.DataSegment;
import junit.framework.Assert;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class CostBalancerStrategyTest
{
  private final List<ServerHolder> serverHolderList = Lists.newArrayList();
  private final Interval day = DateTime.now().toDateMidnight().toInterval();

  /**
   * Create Druid cluster with 10 servers having 100 segments each, and 1 server with 98 segment
   * Cost Balancer Strategy should assign the next segment to the server with less segments.
   */
  @Before
  public void setup(){

    // Create 10 servers with current size being 3K & max size being 10K
    // Each having having 100 segments
    for (int i = 0 ; i < 10; i++){
      LoadQueuePeonTester fromPeon = new LoadQueuePeonTester();
      DruidServer druidServer = EasyMock.createMock(DruidServer.class);
      EasyMock.expect(druidServer.getName()).andReturn("DruidServer_Name_" + i).anyTimes();
      EasyMock.expect(druidServer.getCurrSize()).andReturn(3000L).anyTimes();
      EasyMock.expect(druidServer.getMaxSize()).andReturn(10000000L).anyTimes();

      EasyMock.expect(druidServer.getSegment(EasyMock.<String>anyObject())).andReturn(null).anyTimes();
      Map<String, DataSegment> segments = Maps.newHashMap();
      for (int j = 0; j < 100; j ++){
        DataSegment segment = getSegment(j);
        segments.put(segment.getIdentifier(), segment);
        EasyMock.expect(druidServer.getSegment(segment.getIdentifier())).andReturn(segment).anyTimes();
      }
      EasyMock.expect(druidServer.getSegments()).andReturn(segments).anyTimes();

      EasyMock.replay(druidServer);
      serverHolderList.add(new ServerHolder(druidServer, fromPeon));
    }

    // The best server to be available for next segment assignment has only 98 Segments
    LoadQueuePeonTester fromPeon = new LoadQueuePeonTester();
    DruidServer druidServer = EasyMock.createMock(DruidServer.class);
    EasyMock.expect(druidServer.getName()).andReturn("BEST_SERVER").anyTimes();
    EasyMock.expect(druidServer.getCurrSize()).andReturn(3000L).anyTimes();
    EasyMock.expect(druidServer.getMaxSize()).andReturn(10000000L).anyTimes();

    EasyMock.expect(druidServer.getSegment(EasyMock.<String>anyObject())).andReturn(null).anyTimes();
    Map<String, DataSegment> segments = Maps.newHashMap();
    for (int j = 0; j < 98; j ++){
      DataSegment segment = getSegment(j);
      segments.put(segment.getIdentifier(), segment);
      EasyMock.expect(druidServer.getSegment(segment.getIdentifier())).andReturn(segment).anyTimes();
    }
    EasyMock.expect(druidServer.getSegments()).andReturn(segments).anyTimes();

    EasyMock.replay(druidServer);
    serverHolderList.add(new ServerHolder(druidServer, fromPeon));
  }

  /**
   * Returns segment with dummy id and size 100
   *
   * @param index
   * @return segment
   */
  private DataSegment getSegment(int index){
    DataSegment segment = EasyMock.createMock(DataSegment.class);
    EasyMock.expect(segment.getInterval()).andReturn(day).anyTimes();
    EasyMock.expect(segment.getIdentifier()).andReturn("DUMMY_SEGID_" + index).anyTimes();
    EasyMock.expect(segment.getDataSource()).andReturn("DUMMY").anyTimes();
    EasyMock.expect(segment.getSize()).andReturn(index * 100L).anyTimes();
    EasyMock.replay(segment);
    return segment;
  }

  @Test
  public void testCostBalancerMultithreadStrategy() throws InterruptedException {
    DataSegment segment = getSegment(1000);

    BalancerStrategy strategy = new CostBalancerStrategy(DateTime.now(DateTimeZone.UTC), 8);
    ServerHolder holder = strategy.findNewSegmentHomeReplicator(segment, serverHolderList);
    Assert.assertNotNull("Should be able to find a place for new segment!!", holder);
    Assert.assertEquals("Best Server should be BEST_SERVER", "BEST_SERVER", holder.getServer().getName());
  }


}
