package medley.simulator;

import medley.utils.TopologyGenerator;
import medley.utils.StatsRecorder;
import medley.utils.ConfigParser;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Simulator {
  private static final Logger LOG = Logger.getLogger(Simulator.class.getName());

  // parameters
  private static int NUM_RUN = 1;
  private static int NUM_SERVER = 10;
  private static long END_TIME = 10000;
  private static int BASE_TIME = 100; // base time unit in millisecond
  private static int PING_RTT = BASE_TIME;
  private static int ROUND_PERIOD_MS = 4 * BASE_TIME; // the period for each round
  private static double POWERK = 0.0;
  private static List<long[]> eventList;
  private static Runtime run = Runtime.getRuntime();

  private static PriorityQueue<Server> serversWithPendingEvents;
  private static long current_time = 0;

  // Not be instantiated
  private Simulator() {
  }

  private static void run(ConfigParser parser, int runNum) throws Exception {
    if (parser == null) throw new Exception("no config file");
    eventList = new ArrayList<>(parser.eventList);
    current_time = 0;

    FileWriter fps_file = new FileWriter(parser.membership_path + "fps.txt");
    EventServiceFactory eventServiceFactory = new EventServiceFactory(parser.length);
    parser.copyProperties(eventServiceFactory);
    serversWithPendingEvents = new PriorityQueue<>();
    String root = System.getProperty("user.dir") + "/";
    String topoPath = root + parser.topology_path;
    String cpath = root + parser.coordinate_path;
    if (!cpath.contains(".txt")) {
      cpath += "_" + runNum + ".txt";
    }
    if (parser.generate_new_topology) {
      cpath = root + parser.coordinate_path.substring(0, parser.coordinate_path.lastIndexOf('.')) + "_" + runNum + ".txt";
      if (parser.eventList.size() == 1) {
        int failedNode = (int) parser.eventList.get(0)[0];
        if (failedNode == 0) {
          TopologyGenerator topologyGenerator = new TopologyGenerator();
          topologyGenerator.setArea(parser.length, parser.length);
          topologyGenerator.setOneHopRadius(parser.RADIUS);
          topologyGenerator.setSize(NUM_SERVER);
          topologyGenerator.build(cpath, parser.topology_type, (int) parser.eventList.get(0)[2]);
        }
      }
    }
    eventList = eventServiceFactory.init(cpath, parser.eventList, parser.optimize_route, parser.routing_path);
    if (parser.mobile_percentage > 0) {
      cpath = root + parser.coordinate_path.substring(0, parser.coordinate_path.lastIndexOf('.')) + "_moved_" + runNum + ".txt";
      eventServiceFactory.moveServers(parser.mobile_percentage, parser.mobile_distance, cpath);
    }

    List<Server> all_servers = new ArrayList<>();

    for (int i = 0; i < NUM_SERVER; ++i) {
      Server server = Server.newBuilder()
              .setDisMapInputPath(topoPath)
              .setPort(i)
              .setPowerParamK(POWERK)
              .build("192.168.0." + i);
      parser.copyProperties(server);
      server.setChangeTable();
      all_servers.add(server);
    }

    StatsRecorder statsRecorder = new StatsRecorder(all_servers, parser, runNum);
    eventServiceFactory.registerStatsRecorder(statsRecorder);

    for (Server server: all_servers) {
      server.setStatsRecorder(statsRecorder);
      server.initializeInSimulator(all_servers);
    }

    long last_ping_time = 0;

    while (current_time < END_TIME) {
      long next_ping_time = last_ping_time + ROUND_PERIOD_MS;
      while (getLatestTime() < current_time) {
        TimeUnit.MILLISECONDS.sleep(100);
      }

      freshServerQueue(all_servers);

      // There are pending events to handle before the next round of ping
      if (serversWithPendingEvents.peek() != null && serversWithPendingEvents.peek().next_event_time <= next_ping_time) {
        Server next_server = serversWithPendingEvents.poll();
        if (next_server != null) {
          current_time = next_server.next_event_time;
          next_server.processNextEvent();
        }
        continue;
      }

      // No pending events or pending events are all after the next round of pings
      current_time = last_ping_time;
      for (Server server: all_servers) {
        for (int i = 0; i < eventList.size(); i++) {
          long[] eventParams = eventList.get(i);
          if (server.id.getPort().equals((int)eventParams[0]) && current_time >= eventParams[1] - BASE_TIME && current_time <= eventParams[1] + 4*BASE_TIME) {
            switch ((int) eventParams[2]) {
              case 0:
                server.shutdown(current_time);
                // eventServiceFactory.removeServer(server.id);
                break;
              case 1:
                server.restart(current_time);
                // eventServiceFactory.addServer(server.id);
                break;
              case 2:
                server.shutdown(current_time);
                // eventServiceFactory.removeServer(server.id);
                statsRecorder.conclude(server.id);
                break;
              default:
                System.out.println("no such action");
            }
            eventList.remove(i);
            break;
          }
        }
        Id targetId = server.pingNext(current_time);
        server.checkIndirectPings(current_time + PING_RTT, targetId);
        server.checkFailures(current_time + 4 * PING_RTT - 1);
        server.checkSelfUnlucky(current_time, false);
      }
      last_ping_time = next_ping_time;
      // TimeUnit.MILLISECONDS.sleep(15000);

      // Write membership stats to file
      for (Server server: all_servers) {
        server.writeMembership(parser.membership_path);
//        int fps = parser.NUM_SERVER - getMaxFalsePositive(all_servers);
//        fps_file.write(fps + "\n");
      }
    }
    statsRecorder.conclude();
    // statsRecorder.print(parser.VERBOSE);
    statsRecorder.toFile(parser.VERBOSE);

  }

  private static int getMaxFalsePositive(List<Server> servers) {
    HashMap<Id, Integer> counts = new HashMap<>();
    for (Server server: servers) {
      counts.put(server.id, 0);
    }
    for (Server server: servers) {
      List<Id> aliveNodes = server.getAliveNodes();
      for (Id id: aliveNodes) {
        counts.put(id, counts.get(id) + 1);
      }
    }
    return Collections.min(counts.values());
  }

  private static void freshServerQueue(List<Server> all_servers) {
    serversWithPendingEvents.clear();
    for (Server server : all_servers) {
      if (server.has_pending_events && !server.disabled){ serversWithPendingEvents.add(server); }
    }
  }

  private static long getLatestTime() {
    String command = "./scripts/get_time.sh";
    StringBuilder output = new StringBuilder();
    try {
      Process pr = run.exec(command);
      pr.waitFor();
      String s = "";
      BufferedReader stdOutput = new BufferedReader(new InputStreamReader(pr.getInputStream()));
      while ((s = stdOutput.readLine()) != null) {
        output.append(s).append("\n");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    String str = output.toString();
    if (output.toString().length() < 5) {
      return 10000;
    }
    str = str.substring(str.indexOf("at time"));
    return Long.parseLong(str.split(" ")[2].replace("\n", ""));
  }

  public static void main(String[] args) throws Exception {
    String config_name = "config.json";
    ConfigParser parser = new ConfigParser();
    if (args.length > 0) {
      config_name = args[0];
    }
    if (Objects.equals(config_name, "config.json")) {
      System.out.println("change message drop rate with lower hash!");
      System.exit(0);
    }
    LOG.log(Level.FINE, "Parse configuration file");
    if (!parser.parse(config_name)){
      LOG.log(Level.SEVERE, "Failed to parse configuration file");
      return;
    }
    if (parser.wait_for) {
      while (getLatestTime() > 200) {
        TimeUnit.SECONDS.sleep(1);
      }
    }

    NUM_RUN = parser.NUM_RUN;
    NUM_SERVER = parser.NUM_SERVER;
    END_TIME = parser.END_TIME;
    BASE_TIME = parser.BASE_TIME;
    PING_RTT = parser.PING_RTT;
    ROUND_PERIOD_MS = parser.ROUND_PERIOD_MS;
    POWERK = parser.POWERK;

    for (int i = 0; i < NUM_RUN; ++i) {
      System.out.println("run number: " + (i + 1));
      run(parser, i + 1);
    }
  }  
}
