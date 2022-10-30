package medley.simulator;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.sun.tools.javac.util.Pair;
import medley.service.EventService;
import medley.service.Event;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static medley.simulator.Utils.*;
import medley.utils.StatsRecorder;
import org.json.simple.JSONObject;

/**
 * Modification did for simulator:
 *   1. Modified the network service structure
 *   2. Moved Inbound Channel handler logics to here (may move to event service)
 *   3. Add eventService & its factory logic (InboundChannelHandler --> EventService,
 *      InboundChannelHandlerFactory & NetworkService --> EventServiceFactory)
 *      3.1. networkService.send(getAddressFromId(ping_target), ping); -->
 *           networkService.send(getAddressFromId(ping_target), ping);
 *
 *      3.2 (TODO) introduce address
 */

public class Server implements Comparable<Server> {

  private static final Logger LOG = Logger.getLogger(Server.class.getName());
  private static int BASE_TIME = 100; // base time unit in millisecond
  private static int MAX_NUM_CONTACTS = 3;
  private static int NUM_IND_CONTACTS = 3;
  private static int ROUND_PERIOD_MS = 4 * BASE_TIME; // the period for each round
  private static int PING_RTT = BASE_TIME;         // how long to check response
  private static int SUSPECT_TIMEOUT_MS = 16 * BASE_TIME; // the timeout to determine that a suspected is dead
  private static int PASSIVE_FEEDBACK_TIMEOUT = 32 * BASE_TIME;
  private static int UNLUCKY_THRESHOLD = 5 * ROUND_PERIOD_MS;
  private static double UNLUCKY_ALPHA = 0.125;  // Same default parameter value with TCP RTT estimation strategy.
  private static int CHANGE_TABLE_TIMEOUT_MS = 240000; // 120 * BASE_TIME; // the timeout for cleaning up the change table
  private static int CHANGE_TABLE_SIZE = 500;
  static final int DEFAULT_PORT = 7000;

  // Constant added for simulation
  private static double RADIUS = 5;

  public boolean disabled = false;
  public static volatile boolean stopped = false;

//  private final NaiveNetworkService networkService;
  private EventService eventService;
  public Id id;
  private final int port;
  private final ConcurrentHashMap<Id, Pair<Integer, Status>> membershipTable;
  private final ConcurrentHashMap<Id, Long> suspectTable;
  private final ConcurrentHashMap<Id, Integer> suspectCounts;
  private Cache<Id, Pair<Integer, Status>> changeTable;
  private final HashMap<Id, Long> activeTable;
  private final HashSet<Id> neighborTable;
  private final BlockingQueue<Id> receivedIds;
  private InetSocketAddress introducerAddress;
  private final Double powerk;

  public HashMap<String, Double> neighbour_distance;
  private HashMap<Id, Double> initial_prob_list;
  private HashMap<Id, Double> running_prob_list;
  private Double min_prob;

  private Boolean wait_fst_time_seven_node;

  private static volatile boolean stopFlag = false;
  private static final AtomicInteger countForStop = new AtomicInteger(3);

  private RandomBag onePassIds = new RandomBag();

  // For simulation
  public EventServiceFactory networkService;
  public HashMap<Integer, Integer> routingTable = new HashMap<>();
  public boolean has_pending_events = false;
  public long next_event_time = 0;
  public Strategy strategy = Strategy.ACTIVE_FEEDBACK_BAG;

  // For self unlucky check
  private double timePingAvg = Double.MAX_VALUE;
  private long timePingLast = -1;
  private long unluckySendLast = 0;

  private final List<Id> unluckyNeighbors = new ArrayList<>();
  public StatsRecorder statsRecorder;

  public Strategy getStrategy() {
    return strategy;
  }

  @Override
  public int compareTo(Server in) {
    return Long.compare(this.next_event_time, in.next_event_time);
  }

  @SuppressWarnings("unchecked")
  public void writeMembership(String path) {
    JSONObject obj = new JSONObject();
    JSONObject status = new JSONObject();
    JSONObject suspects = new JSONObject();
    for (Id id: membershipTable.keySet()) {
      String st = membershipTable.get(id).snd == Status.ACTIVE ? "active" : "suspect";
      status.put(id.getPort(), st);
    }
    obj.put("status", status);
    for (Id id: suspectCounts.keySet()) {
      suspects.put(id.getPort(), suspectCounts.get(id).toString());
    }
    obj.put("suspects", suspects);
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    JsonElement je = JsonParser.parseString(obj.toJSONString());
    String prettyJson = gson.toJson(je);

    boolean success = false;
    while (!success) {
      try {
        FileWriter file = new FileWriter(path + id.getPort() + ".json");
        file.write(prettyJson);
        file.flush();
        file.close();
        success = true;
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public void shutdown(long time) {
    if (disabled)
      return;

    LOG.log(Level.INFO, "{0} shuts down.", this.id);
    eventService.shutdown();
    membershipTable.remove(this.id);
    this.statsRecorder.setDeath(this.id, time);
    disabled = true;
  }

  public void restart(long time) {
    if (!disabled)
      return;
    eventService.restart(time);
    this.statsRecorder.restart(this.id, time);
    disabled = false;
  }

  public void rejoin(long time) {
    if (!disabled)
      return;
    membershipTable.remove(this.id);
    this.id = Id.newBuilder()
                .setHostname(this.id.getHostname())
                .setPort(this.id.getPort())
                .setTs(time)
                .build();
    membershipTable.put(this.id, new Pair<>(membershipTable.get(id).fst + 1, Status.ACTIVE));
    initial_prob_list = updateInitialProbabilityList();
    running_prob_list.putAll(initial_prob_list);
    this.suspectTable.clear();
    for (Id server : membershipTable.keySet())
      activeTable.put(server, 0L);
    LOG.log(Level.INFO, "{0} attempts to rejoin the network.", this.id);
    networkService.addServer(id);
    this.statsRecorder.rejoin(this.id, time);
    eventService.setID(this.id);
    eventService.join(time);
    routingTable = networkService.getRoutingTable(id);
    neighbour_distance = networkService.getNeighbourDistance(id);
    disabled = false;
  }

  public static Server.Builder newBuilder() {
    return new Builder();
  }

  private Server(final Id id, final int port, final InetSocketAddress address, final String disMapInputPath, final Double powerk) {
    this.id = id;
    this.port = port;
    this.membershipTable = new ConcurrentHashMap<>();
    this.suspectTable = new ConcurrentHashMap<>();
    this.suspectCounts = new ConcurrentHashMap<>();
    this.receivedIds = new ArrayBlockingQueue<>(3 * NUM_IND_CONTACTS);
    this.neighborTable = new HashSet<>();
    this.activeTable = new HashMap<>();
    this.introducerAddress = address;
//    final ServerInboundChannelHandlerFactory serverInboundChannelHandlerFactory =
//        new ServerInboundChannelHandlerFactory(id, membershipTable, changeTable, suspectTable, receivedIds);
//    final ChannelHandlerFactory outboundChannelHandlerFactory =
//        new SimpleChannelHandlerFactory(OutboundChannelHandler.class);
//    this.networkService = NaiveNetworkService.newBuilder()
//        .setServerGroupNum(2) // to be simple
//        .addServerChannelHandlerFactory(serverInboundChannelHandlerFactory)
//        .addServerChannelHandlerFactory(outboundChannelHandlerFactory)
//        .addClientChannelHandlerFactory(new SimpleChannelHandlerFactory(ClientInboundChannelHandler.class))
//        .addClientChannelHandlerFactory(outboundChannelHandlerFactory)
//        .setMessageType(Message.class)
//        .build();
//    serverInboundChannelHandlerFactory.setNetworkService(networkService);
    this.running_prob_list = new HashMap<>();
    this.min_prob = -1.;
    // this.neighbour_distance = readDistance(disMapInputPath);
    this.powerk = powerk;
    this.wait_fst_time_seven_node = Boolean.TRUE;

    // For simulator
  }

  public void setChangeTable() {
    this.changeTable = CacheBuilder.newBuilder()
      .expireAfterWrite(CHANGE_TABLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      .maximumSize(CHANGE_TABLE_SIZE)
      .build();
    this.networkService = EventServiceFactory.getInstance();
    this.eventService = new EventService(this, membershipTable, changeTable, suspectTable, suspectCounts, receivedIds);
    this.neighbour_distance = networkService.getNeighbourDistance(id);
    this.routingTable = networkService.getRoutingTable(id);
  }

  public void setStatsRecorder(StatsRecorder statsRecorder){
    this.statsRecorder = statsRecorder;
  }

  public RandomBag getOnePassIds() {
    return this.onePassIds;
  }

  private HashMap<String, Double> readDistance(String disMapInputPath) {
    HashMap<String, Double> distances = new HashMap<>();
    try {
      BufferedReader reader = new BufferedReader(new FileReader(disMapInputPath));
      String line = reader.readLine();
      int num_neighbor = Integer.parseInt(line);

      Map<Integer, String> index2Dst = new HashMap<>();
      String myAddr = id.getHostname() + ":" + id.getPort();
      int myIndex = -1;
      for (int i = 0; i < num_neighbor; ++i) {
        line = reader.readLine();
        if (line == null) {
          throw new IOException();
        }
        index2Dst.put(i, line);
        if (line.equals(myAddr)) {
          myIndex = i;
        }
      }
      if (myIndex == -1) {
        LOG.log(Level.INFO, "No distance information about neighbors found");
      }

      for (line = reader.readLine(); line != null; line = reader.readLine()) {
        List<String> lineVector = Arrays.asList(line.split(","));
        int src = Integer.parseInt(lineVector.get(0));
        if (src != myIndex) {
          continue;
        }
        String dst = lineVector.get(1);
        Double dis = Double.parseDouble(lineVector.get(2));
        distances.put(index2Dst.get(Integer.parseInt(dst)), dis);

        // for simulation: routing table
        if (lineVector.size() > 3) {
          String nexthop = lineVector.get(3);
          routingTable.put(Integer.parseInt(dst), Integer.parseInt(nexthop));
        } else {
          routingTable.put(Integer.parseInt(dst), Integer.parseInt(dst));
        }
        // end for simulation: routing table
      }


      reader.close();
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Fail to read distance", e);
    } finally {
      LOG.log(Level.INFO, "Shutdown network service");
//      networkService.stop();
//      networkService.waitForClose();
    }
    return distances;
  }

  public void updateActive(Id activeId, long current_time) {
    activeTable.put(activeId, current_time);
  }

  private void initialize() throws InterruptedException {
    LOG.log(Level.FINE, "Put itself {0} into the membership list", id);
    membershipTable.put(id, new Pair<>(0, Status.ACTIVE));
    changeTable.put(id, new Pair<>(0, Status.JOIN));
    if (introducerAddress != null) {
      join();
    }
    initial_prob_list = updateInitialProbabilityList();
    running_prob_list.putAll(initial_prob_list);
  }

  private void join() throws InterruptedException {
    // send a message to introducer
    LOG.log(Level.INFO, "Send a join message to the introducer {0}", introducerAddress);
    final Message msg = Message.newBuilder()
        .setType(Type.JOIN)
        .setSenderId(id)
        .setCreatorId(id)
        .setSenderIncarnation(membershipTable.get(id).fst)
        .build();

//    networkService.send(introducerAddress, msg);

    // wait for a response for the join message
    if (receivedIds.poll(3000, TimeUnit.MILLISECONDS) == null) {
      // failed to receive a response for join
      // try agin
//      networkService.send(introducerAddress, msg);
      if (receivedIds.poll(3000, TimeUnit.MILLISECONDS) == null) {
        // hasn't received any message from the introducer
        throw new RuntimeException("Failed to receive a membership list from the introducer " + introducerAddress);
      }
    }
  }

  public void updateRunningProbList(HashMap<Id, Double> prob_list) {
    running_prob_list.clear();
    running_prob_list.putAll(prob_list);
  }

  public HashMap<Id, Double> updateInitialProbabilityList() {
    HashMap<Id, Double> prob_list = new HashMap<>();
    Double prob_sum = 0.0;

    Set<Id> keySet = membershipTable.keySet();
    for (final Id targetId : keySet) {
      if (targetId.equals(id)) {
        continue;
      }
      Double prob = 1 / Math.pow(neighbour_distance.get(getAddrfromId(targetId)), powerk);
      prob_list.put(targetId, prob);
      prob_sum += prob;
    }
    // Do normalization
    if (prob_list.size() > 0 && prob_sum > 0) {
      for (final Id targetId : prob_list.keySet()) {
        prob_list.put(targetId, prob_list.get(targetId) / prob_sum);
      }
    }

    // [FEEDBACK DESIGN] increase probability for self-reported unlucky nodes.
    // Current design: make the probability to the average of who has prob > unlucky prob.
    for (final Id unluckyId: unluckyNeighbors) {
      if (membershipTable.containsKey(unluckyId)) {
        prob_list = getToHigherAverage(prob_list, unluckyId);
      }

    }

    if (prob_list.size() > 0) {
      min_prob = Collections.min(prob_list.values());
    } else {
      min_prob = -1.;
    }
    return prob_list;
  }

  public HashMap<Id, Double> setInitialProbabilityList(HashMap<Id, Double> prob_list){
    this.initial_prob_list = new HashMap<Id, Double>();
    this.initial_prob_list.putAll(prob_list);
    return prob_list;
  }

  public void run() {
    LOG.log(Level.INFO, "Server ({0}) gets started", id);
    try {
//      networkService.start(port);

      initialize();

      while (!stopped) {
        // generate a ping message
        final Message ping = Message.newBuilder()
                .setType(Type.PING)
                .setSenderId(id)
                .setCreatorId(id)
                .setInitiatorId(id)
                .setSenderIncarnation(membershipTable.get(id).fst)
                .build();

        // LOG.log(Level.FINER, "Fetch active ids from the membership table");
        RandomBag onePassIds = getBagInstances();
        LOG.log(Level.FINE, "#TSIZE {0} {1}", new String[]{String.valueOf(membershipTable.size()), String.valueOf(onePassIds.size())});
        // No pings when there is only one node in the network.
        if (membershipTable.size() == 1 || onePassIds.size() == 0) {
          TimeUnit.MILLISECONDS.sleep(ROUND_PERIOD_MS);
          continue;
        }
        if (wait_fst_time_seven_node == Boolean.TRUE) {
          initial_prob_list = updateInitialProbabilityList();
          running_prob_list.clear();
          running_prob_list.putAll(initial_prob_list);
          onePassIds = getBagInstances();
          if (membershipTable.size() == 7) {
            onePassIds = getBagInstances();
            wait_fst_time_seven_node = Boolean.FALSE;
            LOG.log(Level.INFO, "full membership size of 7 collected");
          }
        }

        while (!stopped && onePassIds.size() > 0) {
          // TODO: maybe make a quicker reaction to new joined node. (currently is pinging since the next pass.)
          LOG.log(Level.FINER, "Start to ping");

          // pick one node to ping
          Id ping_target = onePassIds.poll();
          while (ping_target != null && !membershipTable.containsKey(ping_target)) { // only send a ping to ACTIVE or SUSPECTED one
            ping_target = onePassIds.poll();
          }
          if (ping_target == null) {
            continue;
          }
          // send out PING msg
          LOG.log(Level.FINER, "Send a ping to {0}", ping_target);
          // networkService.send(ping_target, ping);

          TimeUnit.MILLISECONDS.sleep(PING_RTT);

          // check responses for PING msg
          final List<Id> receivedList = new ArrayList<>(3 * NUM_IND_CONTACTS);
          receivedIds.drainTo(receivedList);
          if (receivedList.size() > 0) {
            LOG.log(Level.FINER, "Get Received message from {0}", receivedList.get(0));
          }
          if (receivedList.indexOf(ping_target) != -1) {
            // receive ack, wait for the next round
            LOG.log(Level.FINER, "------------ Get Correct ACK -----------");
            TimeUnit.MILLISECONDS.sleep(ROUND_PERIOD_MS - PING_RTT);
          } else {
            // has not received a ack from ping target
            // send out indirect pings
            final List<Id> ind_targets = getIndTargets(onePassIds, ping_target);
            // TODO: use a separate queue for IND_PING
            final Message ind_ping = Message.newBuilder()
                .setSenderId(id)
                .setTargetId(ping_target)
                .setCreatorId(id)
                .setType(Type.IND_PING)
                .setTable(transformMapToList(changeTable.asMap()))
                .build();
            for (final Id targetId : ind_targets) {
              LOG.log(Level.FINER, "Send an ind_ping to {0}", targetId);
              // networkService.send(targetId, ind_ping);
            }

            // check final ack at the end of period
            TimeUnit.MILLISECONDS.sleep(ROUND_PERIOD_MS - PING_RTT);

            final List<Id> receivedIndList = new ArrayList<>(3 * NUM_IND_CONTACTS);
            receivedIds.drainTo(receivedIndList);
            if (receivedList.indexOf(ping_target) == -1) {
              // no one received a ack from ping target
              // suspect it to be dead
              if (membershipTable.get(ping_target) != null && membershipTable.get(ping_target).snd == Status.ACTIVE &&
                  suspectTable.putIfAbsent(ping_target, System.currentTimeMillis()) == null) { // put it into the suspectTable
                // it is suspected for the first time.
                LOG.log(Level.INFO, "Not received a response from {0}. SUSPECT", ping_target);
                int incarnation = membershipTable.get(ping_target).fst;
                membershipTable.put(ping_target, new Pair<>(incarnation, Status.SUSPECTED)); // it should be active before
                changeTable.put(ping_target, new Pair<>(incarnation, Status.SUSPECTED));
                suspectCounts.put(ping_target, suspectCounts.getOrDefault(ping_target, 0) + 1);
              }
            }
          }

          // check suspects whether it is timed out
          final long currentTime = System.currentTimeMillis();
          for (final Map.Entry<Id, Long> entry : suspectTable.entrySet()) {
            final Id targetId = entry.getKey();
            if (currentTime - entry.getValue() >= SUSPECT_TIMEOUT_MS) {
              if (membershipTable.get(targetId) != null && membershipTable.get(targetId).snd == Status.SUSPECTED) {
                LOG.log(Level.INFO, "{0} is considered FAILED by {1}", new Object[]{targetId, id});
                changeTable.put(targetId, new Pair<>(membershipTable.get(targetId).fst, Status.FAILED));
                membershipTable.remove(targetId); // remove from the membership table
                initial_prob_list = updateInitialProbabilityList();
                running_prob_list.clear();
                running_prob_list.putAll(initial_prob_list);
              }
              // already mark it as failed.
              // just remove it from the suspected table if necessary.
              suspectTable.remove(targetId); // remove from suspectTable
            }
          }
        }
      }
    } catch (final Exception e) {
      StringWriter sw = new StringWriter();
      e.printStackTrace(new PrintWriter(sw));
      LOG.log(Level.SEVERE, sw.toString());
    } finally {
      LOG.log(Level.INFO, "Shutdown network service");
//      networkService.stop();
//      networkService.waitForClose();
    }
  }

//  private Queue<Id> getOnePassIds() {
//    final List<Id> ids = new ArrayList<>(membershipTable.size());
//
//    if (running_prob_list.size() == 0) {
//      initial_prob_list = updateInitialProbabilityList();
//      running_prob_list.putAll(initial_prob_list);
//    }
//
//    for (final Id targetId : membershipTable.keySet()) {
//      if (!running_prob_list.containsKey(targetId) || id.equals(targetId)) {
//        continue;
//      }
//      ids.add(targetId);
//    }
//    for (final Id targetId : ids) {
//      running_prob_list.put(targetId, running_prob_list.get(targetId) - min_prob);
//      if (running_prob_list.get(targetId) <= 0) {
//        running_prob_list.remove(targetId);
//      }
//    }
//
//    // TODO: maybe here update prob list when membership changes.
//    Collections.shuffle(ids);
//    return new ArrayDeque<>(ids);
//  }

  private RandomBag getBagInstances() {
    final RandomBag ids = new RandomBag();
    for (final Id targetId : membershipTable.keySet()) {
      if (Objects.equals(id.getPort(), targetId.getPort())) {
        continue;
      }
      Double scanValue = initial_prob_list.get(targetId);
      if (scanValue == null) {
        initial_prob_list = updateInitialProbabilityList();
        return getBagInstances();
      }
      final long single_num_inst = (long) Math.ceil(scanValue / min_prob);
      ids.addInstance(targetId, single_num_inst);
    }
    LOG.log(Level.FINE, "Node {0} begins a new super-round with {1} instances", new Object[]{id.getPort(), ids.size()});
    return ids;
  }

  public List<Id> getIndTargets(RandomBag onePassIds, Id ping_target) {
    final List<Id> targets = new ArrayList<>(NUM_IND_CONTACTS);
    for (int i = 0; i < NUM_IND_CONTACTS; ++i) {
      if (onePassIds.size() <= 0) {
        onePassIds = getBagInstances();
      }
      final Id target = onePassIds.poll();
      if (membershipTable.containsKey(target) && !targets.contains(target) && (!target.equals(ping_target))) {
        // only send a ping to ACTIVE or SUSPECTED one, no duplicate
        targets.add(target);
      }
    }

    for (final Id target_id : membershipTable.keySet()) {
      if (targets.size() >= NUM_IND_CONTACTS || targets.size() >= membershipTable.size()) {
        break;
      }
      if (!targets.contains(target_id) && !target_id.equals(ping_target) && !target_id.equals(id)) {
        targets.add(target_id);
      }
    }
    return targets;
  }

  private Queue<Id> getActiveIds() {
    final List<Id> ids = new ArrayList<>(membershipTable.size());
    for (final Id targetId : membershipTable.keySet()) {
      if (id.equals(targetId)) {
        continue;
      }
      ids.add(targetId);
    }
    // for random pick
    Collections.shuffle(ids);

    return new ArrayDeque<>(ids);
  }

  private Id getIdfromAddress(String addr) { // Match [IP]:[PORT] to Id
    Queue<Id> activeIds = getActiveIds();
    for (final Id single_id : activeIds) {
      if (addr.equals(single_id.getHostname() + ":" + single_id.getPort())) {
        return single_id;
      }
    }
    return null;
  }

  private String getAddrfromId(Id single_id) { // Fetch Id to String [IP]:[PORT]
    return single_id.getHostname() + ":" + single_id.getPort();
  }

  public List<Id> getNeighbors() {
    return new ArrayList<>(this.neighborTable);
  }

  public void unluckyNeighborHandler(Id unlucky) {
    LOG.log(Level.FINE, "Node {0} receives node {1} unlucky. Updating probability list...", new Object[]{id.getPort(), unlucky.getPort()});
    unluckyNeighbors.add(unlucky);
    initial_prob_list = getToHigherAverage(initial_prob_list, unlucky);
    updateOnePassIdForUnluckyNode(unlucky);
  }

  static final class Builder {
    private int port = DEFAULT_PORT;
    private InetSocketAddress introducerAddress;
    private String disMapInputPath;
    private Double powerk;

    private Builder() {
    }

    public Builder setPort(final int port) {
      this.port = port;
      return this;
    }

    public Builder setIntroducerAddress(final InetSocketAddress address) {
      this.introducerAddress = address;
      return this;
    }

    public Builder setDisMapInputPath(final String path) {
      this.disMapInputPath = path;
      return this;
    }

    public Builder setPowerParamK(Double powerk) {
      this.powerk = powerk;
      return this;
    }

    public Server build(String hostName) {
      // generate Id
      final Id newID = Id.newBuilder()
          .setHostname(hostName)
          .setPort(port)
          .setTs(0L) //.setTs(System.currentTimeMillis())
          .build();
      return new Server(newID, port, introducerAddress, disMapInputPath, powerk);
    }
  }

  private static String getLocalhostAddress() {
    try {
      String interfaceName = "wlan0";
      NetworkInterface networkInterface;
      try {
        networkInterface = NetworkInterface.getByName(interfaceName);
        if (networkInterface == null) {
          return Inet4Address.getLocalHost().getHostAddress();
        }
      } catch (SocketException e) {
        return Inet4Address.getLocalHost().getHostAddress();
      }
      Enumeration<InetAddress> inetAddress = networkInterface.getInetAddresses();
      InetAddress currentAddress;
      while (inetAddress.hasMoreElements()) {
        currentAddress = inetAddress.nextElement();
        if (currentAddress instanceof Inet4Address && !currentAddress.isLoopbackAddress()) {
          return currentAddress.getHostAddress();
        }
      }
      throw new UnknownHostException();
    } catch (final UnknownHostException e) {
      LOG.log(Level.SEVERE, "Failed to get localhost name", e);
      throw new RuntimeException(e);
    }
  }


  /**  Add for simulator
   * */

  /*
  *   handleACKCHECK:
  *   DESCRIPTION: handle ACK_CHECK event*/
  public void handleACKCHECK(Event event){
    final List<Id> receivedList = new ArrayList<>(3 * NUM_IND_CONTACTS);
    receivedIds.drainTo(receivedList);
    if (receivedList.size() > 0) {
      LOG.log(Level.FINER, "Node {0} Received ACK from node {1}", new Object[]{id.getPort(), receivedList.get(0).getPort()});
    }
    if (receivedList.contains(event.eventTarget)) {
      // receive ack, wait for the next round
      LOG.log(Level.FINER, "------------ Get Correct ACK -----------");
    } else {
      // has not received a ack from ping target
      // send out indirect pings
      final List<Id> ind_targets = getIndTargets(getOnePassIds(), event.eventTarget);
      final Message ind_ping = Message.newBuilder()
              .setSenderId(id)
              .setInitiatorId(id)
              .setTargetId(event.eventTarget)
              .setCreatorId(id)
              .setType(Type.IND_PING)
              .setTable(transformMapToList(changeTable.asMap()))
              .setSenderIncarnation(membershipTable.get(id).fst)
              .build();
      for (final Id targetId : ind_targets) {
        LOG.log(Level.FINER, "Node {0} send ind_ping to {1} for {2}",
            new Object[]{id.getPort(), targetId.getPort(), event.eventTarget.getPort()});
//        networkService.send(targetId, ind_ping, event.eventTime);
        this.eventService.sendMessage(targetId, ind_ping, event.eventTime);
      }
      networkService.indAckCheck(event.eventTime + 3 * PING_RTT, event.eventTarget, id);
    }
  }

  /*
   *   handleINDACKCHECK:
   *   DESCRIPTION: handle IND_ACK_CHECK event*/
  public void handleINDACKCHECK(Event event){
    final List<Id> receivedIndList = new ArrayList<>(3 * NUM_IND_CONTACTS);
    receivedIds.drainTo(receivedIndList);
    if (!receivedIndList.contains(event.eventTarget)) {
      // no one received a ack from ping target
      // suspect it to be dead
      if (membershipTable.get(event.eventTarget) != null && membershipTable.get(event.eventTarget).snd == Status.ACTIVE &&
              suspectTable.putIfAbsent(event.eventTarget, event.eventTime) == null) { // put it into the suspectTable
        // it is suspected for the first time.
        LOG.log(Level.INFO, "{0}: Node {1} not received a response from node {2}. SUSPECT",
            new Object[]{event.eventTime, id.getPort(), event.eventTarget.getPort()});
        int incarnation = membershipTable.get(event.eventTarget).fst;
        membershipTable.put(event.eventTarget, new Pair<>(incarnation, Status.SUSPECTED)); // it should be active before
        changeTable.put(event.eventTarget, new Pair<>(incarnation, Status.SUSPECTED));
        suspectCounts.put(event.eventTarget, suspectCounts.getOrDefault(event.eventTarget, 0) + 1);
      }
    }
  }

  /*
   *   handleFAILCHECK:
   *   DESCRIPTION: handle FAILURE_CHECK event*/
  public void handleFAILCHECK(Event event){
    // LOG.log(Level.INFO, "Node {0} self failure check",id.getPort());
    for (final Map.Entry<Id, Long> entry : suspectTable.entrySet()) {
      final Id targetId = entry.getKey();
      if (event.eventTime - entry.getValue() >= SUSPECT_TIMEOUT_MS) {
        if (membershipTable.get(targetId) != null && membershipTable.get(targetId).snd == Status.SUSPECTED) {
          LOG.log(Level.INFO, "{0}: Node {1} checks {2} as FAILED", new Object[]{event.eventTime, this.id.getPort(), targetId});
          routingTable = networkService.getRoutingTable(id);
          neighbour_distance = networkService.getNeighbourDistance(id);
          changeTable.put(targetId, new Pair<>(membershipTable.get(targetId).fst, Status.FAILED));
          if (membershipTable.remove(targetId) != null) statsRecorder.recordFail(targetId, id, event.eventTime, networkService.getDistance(id, targetId)); // remove from the membership table
          HashMap<Id, Double> prob_list = updateInitialProbabilityList();
          updateRunningProbList(prob_list);
        }
        // already mark it as failed.
        // just remove it from the suspected table if necessary.
        suspectTable.remove(targetId); // remove from suspectTable
      }
    }
  }

  public void initializeInSimulator(List<Server> all_servers) throws InterruptedException {
    LOG.log(Level.FINE, "Put itself {0} into the membership list", id);
//    membershipTable.put(id, Status.ACTIVE);
//    changeTable.put(id, Status.JOIN);
//    if (introducerAddress != null) {
//      join();
//    }
    for (Server server : all_servers) {
      membershipTable.put(server.id, new Pair<>(0, Status.ACTIVE));
      activeTable.put(server.id, 0L);

      if (!server.id.equals(this.id) && neighbour_distance.get(getAddrfromId(server.id)) < RADIUS) {
        neighborTable.add(server.id);
      }
    }

    initial_prob_list = updateInitialProbabilityList();
    running_prob_list.putAll(initial_prob_list);
  }

  public void updateNextEventTime(long time) {
    if (time == Long.MAX_VALUE) {
      this.has_pending_events = false;
    } else {
      this.has_pending_events = true;
      this.next_event_time = time;
    }
  }

  public void processEventsBeforeTs(long time) throws Exception {
    this.eventService.executeEventBeforeTs(time);
    this.updateNextEventTime(this.eventService.nextEventTs);
  }

  public void processNextEvent() throws Exception {
    this.eventService.executeNextEvent();
    this.updateNextEventTime(this.eventService.nextEventTs);
  }

  public Id pingNext(long current_time) throws InterruptedException {
    if (disabled)
      return id;
    // Generate ping message
    final Message ping = Message.newBuilder()
        .setType(Type.PING)
        .setSenderId(id)
        .setInitiatorId(id)
        .setCreatorId(id)
        .setSenderIncarnation(membershipTable.get(id).fst)
        .build();

    // get next ping target
    Id targetId = id;
    if (strategy.equals(Strategy.NAIVE) || strategy.equals(Strategy.PASSIVE_FEEDBACK)) {
      targetId = getNextTargetForNative();
    } else if (strategy.equals(Strategy.NAIVE_BAG) || strategy.equals(Strategy.PASSIVE_FEEDBACK_BAG) ||
            strategy.equals(Strategy.ACT_PAS_FEEDBACK_BAG) || strategy.equals(Strategy.ACTIVE_FEEDBACK_BAG)) {
      targetId = getNextTargetForBagStrategy(current_time);
    }

    LOG.log(Level.FINER, "{0}: {1} send ping to {2}", new Object[]{current_time, id.getPort(), targetId.getPort()});
    // Pings
    this.eventService.sendMessage(targetId, ping, current_time);
    return targetId;
  }

  private Id getNextTargetForNative() {
    // TODO (@ruiyang)
    return id;
  }

  private Id getNextTargetForBagStrategy(long current_time) {
    Random rand = new Random();
    // conducts passive feedback with probability (for aggressiveness control)
    if (rand.nextFloat() < 0.1) {
      if (strategy.equals(Strategy.ACT_PAS_FEEDBACK_BAG) || strategy.equals(Strategy.PASSIVE_FEEDBACK_BAG)) {
        // actively check whether there is a node has not been updated for a while.
        List<Id> keyset = new ArrayList<>(membershipTable.keySet());
        Collections.shuffle(keyset);
        for (Id checkId : keyset) {
          if (!checkId.equals(id) && current_time - activeTable.get(checkId) > PASSIVE_FEEDBACK_TIMEOUT) {
            // TODO: increase this node's pinging probability?
            LOG.log(Level.INFO, "{0}: Node {1} finds node {2} unlucky. Starting pinging it...", new Object[]{current_time, id.getPort(), checkId.getPort()});
            statsRecorder.recordPassiveUnlucky(this.id, checkId);
            return checkId;
          }
        }
      }
    }

    // Original BAG strategy
    Id ping_target = null;
    // TODO: if membershiptable is empty, this will cause an infinite loop (keyword: super-round with 0 instance)
    while (ping_target == null) {
      ping_target = onePassIds.poll();

      if (ping_target == null) { // onePassIds is empty.
        onePassIds = getBagInstances();
        continue;
      }

      if (!membershipTable.containsKey(ping_target)) { // only send a ping to ACTIVE or SUSPECTED target
        ping_target = null;
      }
    }
    return ping_target;
  }

  /*
  *   checkIndirectPings:
  *   DESCRIPTION: add indirect ping check event to target server*/
  public void checkIndirectPings(long checkTime, Id targetId) {
    if (disabled)
      return;
    networkService.ackCheck(checkTime, targetId, id);
  }

  /*
   *   checkFailures:
   *   DESCRIPTION: add failure check event to target server*/
  public void checkFailures(long checkTime) {
    if (disabled)
      return;
    networkService.failCheck(checkTime, id);
  }

  private void reportUnlucky(long current_time) {
    if (current_time - unluckySendLast < UNLUCKY_THRESHOLD) {
      LOG.log(Level.FINE, "Node {0} find itself unlucky. But just reported.", id.getPort());
      return;
    }
    LOG.log(Level.FINE, "Node {0} find itself unreported unlucky. Sending message to neighbors...", id.getPort());
    statsRecorder.recordActiveUnlucky(id, current_time);
    List<Id> neighbors = getNeighbors();
    final Message unlucky_msg = Message.newBuilder()
        .setType(Type.UNLUCKY)
        .setSenderId(id)
        .setCreatorId(id)
        .setInitiatorId(id)
        .setSenderIncarnation(membershipTable.get(id).fst)
        .build();

    for (Id neighbor: neighbors) {
      this.eventService.sendMessage(neighbor, unlucky_msg, current_time);
      // this.networkService.send(neighbor.getPort(), unlucky_msg, current_time);
    }
    unluckySendLast = current_time;
  }

  public void checkSelfUnlucky(long current_time, boolean ping_received) {
    if (disabled) { return; }
    if (strategy.equals(Strategy.NAIVE) ||
        strategy.equals(Strategy.NAIVE_BAG) ||
        strategy.equals(Strategy.PASSIVE_FEEDBACK) ||
        strategy.equals(Strategy.PASSIVE_FEEDBACK_BAG)
    ) { return; }
    // System.out.println(current_time + "," + id.getPort() + "," + timePingAvg + "," + timePingLast);
    LOG.log(Level.FINE, "{0}: Node {1} self unlucky check", new Object[]{current_time, id.getPort()});

    // warm up checks without ping
    if (current_time < UNLUCKY_THRESHOLD  && !ping_received) {
      return;
    }

    // ping received
    if (ping_received) {
      timePingAvg = timePingLast < 0 ?
                    current_time :
                    UNLUCKY_ALPHA * (current_time - timePingLast) + (1 - UNLUCKY_ALPHA) * timePingAvg;
      timePingLast = current_time;

      if (timePingAvg > UNLUCKY_THRESHOLD) {
        reportUnlucky(current_time);
      }
    }

    // regular checks without a ping
    if (timePingLast < 0) { // No ping since the start.
      reportUnlucky(current_time);
    } else { // There are pings before.
      double avg = UNLUCKY_ALPHA * (current_time - timePingLast) + (1 - UNLUCKY_ALPHA) * timePingAvg;
      if (avg > UNLUCKY_THRESHOLD) {
        reportUnlucky(current_time);
      }
    }
  }


  /**
   * For ACTIVE FEEDBACK
   * Bring the probability of unluckyId to the average value of probs which are originally
   * same with or higher than lucky nodes.
   *
   * For example,
   *    Four nodes with probability distribution of [0.43, 0.32, 0.15, 0.1], with 3rd node
   *    as unlucky node. The eventual target is to bring 0.15 to (0.43 + 0.32 + 0.15) / 3
   *    = 0.3. There are two nodes with prob > 0.3, which is 0.43 and 0.32. They'll propor-
   *    tionally offer extra probability. They have extra prob of 0.13 + 0.02 = 0.15 in total.
   *    0.43 gives (0.43 - 0.3) / 0.15 * (0.3 - 0.15) = 0.13, and similarly, 0.32 gives 0.02.
   *    Eventually the prob list is [0.3, 0.3, 0.3, 0.1].
   * */
  private HashMap<Id, Double> getToHigherAverage(HashMap<Id, Double> prob_list, Id unluckyId) {
    if (!prob_list.containsKey(unluckyId)) {
      LOG.log(Level.WARNING, "{0}: Unlucky node {1} not recognized.", new Object[]{id, unluckyId});
      return prob_list;
    }
//    System.out.println("before probability list: " + prob_list);
//    double sum = 0.0;
//    for (Id checkId : prob_list.keySet()) {
//      sum += prob_list.get(checkId);
//    }
//    System.out.println("sum is: " + sum);

    // get nodes that are has higher probability than unlucky nodes
    double prob = prob_list.get(unluckyId);
    double above_prob_total = 0.0;
    List<Id> freq_ids = new ArrayList<>();
    HashMap<Id, Double> res_prob_list = new HashMap<>();
    for (Map.Entry<Id, Double> entry: prob_list.entrySet()) {
      if (entry.getValue() > prob) {
        above_prob_total += entry.getValue();
        freq_ids.add(entry.getKey());
      } else { // no modification for nodes with probabilities even lower than unlucky nodes.
        res_prob_list.put(entry.getKey(), entry.getValue());
      }
    }

    // get the probability value that unlucky node needs to improve
    double above_prob_avg = (above_prob_total + prob) / (freq_ids.size() + 1);
    double unlucky_diff = above_prob_avg - prob;

    // bring unlucky node prob to above_prob_avg. Probs comes from nodes who has probs > above_prob_avg.
    double sum_freq = 0.0;
    for (Id freq_id: freq_ids) {
      double prob_freq = prob_list.getOrDefault(freq_id, 0.0);
      if (prob_freq > above_prob_avg) {
        sum_freq += prob_freq - above_prob_avg;
      }
    }

    double total_lend = 0.0;
    for (Id freq_id: freq_ids) {
      double prob_freq = prob_list.getOrDefault(freq_id, 0.0);
      if (prob_freq > above_prob_avg) {
        double amount_lend = (prob_freq - above_prob_avg) / sum_freq * unlucky_diff;
        total_lend += amount_lend;
        res_prob_list.put(freq_id, prob_freq - amount_lend);
      } else {
        res_prob_list.put(freq_id, prob_freq);
      }
    }
    res_prob_list.put(unluckyId, prob + total_lend);
//    System.out.println("after probability list: " + res_prob_list);
//    sum = 0.0;
//    for (Id checkId : res_prob_list.keySet()) {
//      sum += res_prob_list.get(checkId);
//    }
//    System.out.println("sum is: " + sum);

    return res_prob_list;
  }

  private void updateOnePassIdForUnluckyNode(Id unlucky) {
    // When there is an unlucky node reported. Ignore what already happened
    // in this pass. Add instances for unlucky nodes so that new OnePassIds
    // will ping unlucky node with new probability.
    long pass_size = onePassIds.size();
    if (!initial_prob_list.containsKey(unlucky)){
      LOG.log(Level.WARNING, "{0}: {1} notify unlucky but id not recognized", new Object[]{this.id, unlucky});
      return;
    }
    double unlucky_prob = initial_prob_list.get(unlucky);
    long existing_unlucky = onePassIds.count(unlucky);
    int num_added = (int) Math.ceil((pass_size - existing_unlucky) * unlucky_prob / (1 - unlucky_prob));
    // System.out.println("unlucky node added: " + num_added);
    LOG.log(Level.FINEST, "      Node {0} old rest onePassIds: {1}", new Object[]{id.getPort(), onePassIds});
    onePassIds.addInstance(unlucky, (long)num_added);
    LOG.log(Level.FINEST, "      Node {0} new rest onePassIds: {1}", new Object[]{id.getPort(), onePassIds});
  }

  private class RandomBag{
    private final LinkedHashMap<Id, Long> instanceNum;
    private long ttlInstanceNum;
    private final Random random;
    
    public RandomBag(){
      instanceNum = new LinkedHashMap<>();
      ttlInstanceNum = 0;
      random = new Random();
    }

    @Override
    public String toString() {
      String result = "";
      for (Id currId: instanceNum.keySet()) {
        result += currId.getPort() + ": " + instanceNum.getOrDefault(currId, 0L) + ",  ";
      }
      return "{" + result + "}";
    }

    public void addInstance(Id instanceId, long num){
      if (instanceNum.containsKey(instanceId)){
        instanceNum.put(instanceId, instanceNum.get(instanceId) + num);
      } else {
        instanceNum.put(instanceId, num);
      }
      // LOG.log(Level.INFO, "Node {0} added instance", instanceId);
      ttlInstanceNum += num;
    }

    public Id poll(){
      if (ttlInstanceNum <= 0) return null;
      long lrandom = (random.nextLong() & Long.MAX_VALUE) % ttlInstanceNum; // a random long in range [0, ttlInstanceNum)
      long currIdx = 0;
      for (Id target_id : instanceNum.keySet()){
        long numCount = instanceNum.get(target_id);
        currIdx += numCount;
        if (currIdx >= lrandom){
          instanceNum.put(target_id, numCount - 1);
          if (numCount - 1 == 0)
            instanceNum.remove(target_id);
          ttlInstanceNum--;
          return target_id;
        }
      }
      return null;
    }

    public long size(){
      return (long) ttlInstanceNum;
    }

    public long count(Id currId){
      return instanceNum.containsKey(currId) ? (long) instanceNum.get(currId) : 0;
    }
  }
}
