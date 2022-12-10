package medley.simulator;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import medley.service.Event;
import medley.service.EventService;
import medley.service.EventType;
import medley.utils.StatsRecorder;
import medley.utils.TopologyGenerator;


public class EventServiceFactory {

  private static final Logger LOG = Logger.getLogger(Server.class.getName());

  private final HashMap<Integer, EventService> eventServices;
  private final HashMap<Integer, Id> ids;
  private static EventServiceFactory singleton = null;
  private int DELAY = 20;
  private double RADIUS = 25;
  private int DELAY_ONE_HOP = 10;
  private double MSG_DROP_RATE = 0.0;
  private final Random randDrop;
  private StatsRecorder statsRecorder;
  private final TopologyGenerator topology;
  private HashMap<Id, HashMap<Id, Id>> routingTable;
  private String distance_metric = "none";

  public EventServiceFactory(int length) {
    this.eventServices = new HashMap<>();
    this.ids = new HashMap<>();
    this.randDrop = new Random();
    this.topology = new TopologyGenerator();
    topology.setArea(length, length);
    this.routingTable = new HashMap<>();
    singleton = this;
  }

  public List<long[]> init(String cpath, List<long[]> eventList, boolean optimize_route, String rpath) {
    topology.setOneHopRadius(RADIUS);
    topology.set_locations(cpath, optimize_route, rpath);
    List<long[]> newEventList = topology.generate_failure(eventList);
    routingTable = topology.getRoutingTable();
    return newEventList;
  }

  public HashMap<Integer, Integer> getRoutingTable(Id serverId) {
    HashMap<Integer, Integer> table = new HashMap<>();
    for (Map.Entry<Id, Id> entry : routingTable.get(serverId).entrySet()) {
      if (entry.getKey().equals(serverId))
        continue;
      table.put(entry.getKey().getPort(), entry.getValue().getPort());
    }
    return table;
  }

  public double getDistance(Id server1, Id server2) {
    return topology.getDistanceMap().get(server1).get(server2);
  }

  public HashMap<String, Double> getNeighbourDistance(Id serverId) {
    HashMap<Id, Double> map = new HashMap<>();
    HashMap<String, Double> mapRet = new HashMap<>();
    switch (distance_metric) {
      case "direct_distance":
        map = topology.getDistanceMap().get(serverId);
        break;
      case "hop_distance":
        map = topology.getHopDistMap().get(serverId);
        break;
      case "hop_number":
        map = topology.getHopNumMap().get(serverId);
        break;
      default:
        LOG.log(Level.SEVERE, "no such distance metric.");
        System.exit(0);
    }

    for (Id id : map.keySet()) {
      mapRet.put(topology.getAddrfromId(id), map.get(id));
    }

    // keep failed node info in distance map
    HashMap<String, Double> prevMap = eventServices.get(serverId.getPort()).server.neighbour_distance;
    if (prevMap != null) {
      for (String id : prevMap.keySet()) {
        if (!mapRet.containsKey(id)) {
          mapRet.put(id, prevMap.get(id));
        }
      }
    }

    return mapRet;
  }

  public static synchronized EventServiceFactory getInstance() {
    if (EventServiceFactory.singleton == null) {
      System.out.println("Program should not reach here");
      EventServiceFactory.singleton = new EventServiceFactory(-1);
    }

    return EventServiceFactory.singleton;
  }

  public void registerEventService(EventService es) {
    eventServices.put(es.id.getPort(), es);
    ids.put(es.id.getPort(), es.id);
  }

  public void removeEventService(Id id) {
    eventServices.remove(id.getPort());
    ids.remove(id.getPort());
  }

  public void registerStatsRecorder(StatsRecorder statsRecorder){
    this.statsRecorder = statsRecorder;
  }

  public void clear() {
    eventServices.clear();
    singleton = null;
  }

  public void moveServers(double percentage, double distance, String cpath) {
    topology.move_servers(percentage, distance, cpath);
  }

  public void removeServer(Id id) {
    topology.remove_server(id);
    routingTable = topology.getRoutingTable();
  }

  public void addServer(Id id) {
    topology.add_server(id);
    routingTable = topology.getRoutingTable();
  }

  private Id getId(int index) {
    return this.ids.get(index);
  }

  // not used
  public void send(Id target_id, Message msg, long current_time) {
    send(target_id, target_id, msg, current_time);
  }

  // not used
  public void send(int target_id, Message msg, long current_time) {
    send(target_id, target_id, msg, current_time);
  }

  // not used
  public void send(Id final_receiver, Id next_hop, Message msg, long current_time) {
    if (randDrop.nextDouble() < MSG_DROP_RATE)
      return;
    statsRecorder.ttlIndMsgNum++;
    statsRecorder.incrementIndMsgNum(msg.getSenderId());
    statsRecorder.incrementPairWiseMsgNum(msg.getSenderId(), next_hop);
    Random rand = new Random();
    EventService eventService = eventServices.get(next_hop.getPort());
    Event event = new Event(EventType.REGULAR_PING, final_receiver, current_time + rand.nextInt(DELAY), msg);
    eventService.addEvent(event);
  }

  public void send(int final_receiver, int next_hop, Message msg, long current_time) {
    if ((msg.getType() == Type.IND_PING_ACK || msg.getType() == Type.ACK) && msg.getSenderId().getPort() < 5) {
      if (randDrop.nextDouble() < MSG_DROP_RATE + (5 - msg.getSenderId().getPort()) / 2.0 * MSG_DROP_RATE) {
        return;
      }
    } else if (randDrop.nextDouble() < MSG_DROP_RATE) {
      LOG.log(Level.FINE, "-- Message dropped --");
      return;
    }
    statsRecorder.ttlIndMsgNum++;
    statsRecorder.incrementIndMsgNum(msg.getSenderId());
    statsRecorder.incrementPairWiseMsgNum(msg.getSenderId(), getId(next_hop));
    Random rand = new Random();
    EventService eventService = eventServices.get(next_hop);
    Event event = new Event(EventType.REGULAR_PING, getId(final_receiver), current_time + rand.nextInt(DELAY), msg);
    eventService.addEvent(event);
  }

  public void ackCheck(long checkTime, Id targetId, Id selfId) {
    Message msg = new Message();
    Event event = new Event(EventType.ACK_CHECK, targetId, checkTime, msg);
    EventService eventService = eventServices.get(selfId.getPort());
    eventService.addEvent(event);
  }

  public void indAckCheck(long checkTime, Id targetId, Id selfId) {
    Message msg = new Message();
    Event event = new Event(EventType.IND_ACK_CHECK, targetId, checkTime, msg);
    EventService eventService = eventServices.get(selfId.getPort());
    eventService.addEvent(event);
  }

  public void failCheck(long checkTime, Id selfId) {
    Message msg = new Message();
    Event event = new Event(EventType.FAIL_CHECK, selfId, checkTime, msg);
    EventService eventService = eventServices.get(selfId.getPort());
    eventService.addEvent(event);
  }

//  public void notifyUnlucky(Id target_id, Message msg, long current_time) {
//    if (randDrop.nextDouble() < MSG_DROP_RATE)
//      return;
//    statsRecorder.ttlIndMsgNum++;
//    statsRecorder.incrementIndMsgNum(msg.getSenderId());
//    statsRecorder.incrementPairWiseMsgNum(msg.getSenderId(), target_id);
//    Random rand = new Random();
//    EventService eventService = eventServices.get(target_id.getPort());
//    Event event = new Event(EventType.UNLUCKY_NOTIFICATION, target_id, current_time + rand.nextInt(DELAY_ONE_HOP), msg);
//    eventService.addEvent(event);
//  }
}
