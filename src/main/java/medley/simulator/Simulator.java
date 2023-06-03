package medley.simulator;

import medley.utils.TopologyGenerator;
import medley.utils.StatsRecorder;
import medley.service.Event;
import medley.service.EventType;
import medley.utils.ConfigParser;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumSet;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


import java.io.FileWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import java.lang.Runtime;

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

  private static PriorityQueue<Server> serversWithPendingEvents;
  private static long current_time = 0;

  // Not be instantiated
  private Simulator() {
  }

  private static void insertChurnTrace(ConfigParser parser, List<Server> all_servers) {
    BufferedReader reader;
    try {
			reader = new BufferedReader(new FileReader(parser.churn_path));
			String line = reader.readLine();
      String last_node = "";
      int i = -1;
      Event e = null;
      EventType type = EventType.JOIN;
      long lastTime = 0;
      Random rand = new Random(System.currentTimeMillis());
      
			while (line != null) {
				// System.out.println(line);
        String[] parts = line.split("\t");
        if (!parts[0].equals(last_node)) {
          last_node = parts[0];
          i++;
        }

        if (parts[2].equals("1")){
          type = EventType.JOIN;
        } else if (parts[2].equals("0")){
          type = EventType.LEAVE;
        } else{
          System.out.println("ERROR in parsing file");
          System.exit(1);
        }
        lastTime = (long) (Integer.parseInt(parts[1])*ROUND_PERIOD_MS*parser.CHURN_RATIO);
        lastTime += (rand.nextLong() % ROUND_PERIOD_MS);
        e = new Event(type, null, lastTime, null);
        all_servers.get(i*2).addEvent(e);
				// read next line
				line = reader.readLine();
			}
      e = new Event(EventType.CHURN_PROCESSED, null, lastTime+ROUND_PERIOD_MS, null, parser.membership_path+"done");
      System.out.println("Created processed event at time "+String.valueOf(lastTime+ROUND_PERIOD_MS));
      all_servers.get(1).addEvent(e);
      
      

			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
  }

  private static void run(ConfigParser parser, int runNum) throws Exception {
    if (parser == null) throw new Exception("no config file");
    eventList = new ArrayList<>(parser.eventList);
    current_time = 0;

    // System.out.println("Beginning simulator run");


    
    EventServiceFactory eventServiceFactory = new EventServiceFactory(parser.length);
    parser.copyProperties(eventServiceFactory);
    serversWithPendingEvents = new PriorityQueue<>();
    String root = System.getProperty("user.dir") + "/";
    String topoPath = root + parser.topology_path;
    String cpath = root + parser.coordinate_path;
    // System.out.println("cpath ln 50 Simulator is "+cpath);
    if (!cpath.contains(".txt")) {
      cpath += "_" + runNum + ".txt";
    }
    if (parser.generate_new_topology) {
      cpath = root + parser.coordinate_path.substring(0, parser.coordinate_path.lastIndexOf('.')) + ".txt"; // + "_" + runNum + ".txt";
      if (parser.eventList.size() == 1) {
        int failedNode = (int) parser.eventList.get(0)[0];
        if (failedNode == 0) {
          // System.out.println("Reaching code to generate the topology");
          TopologyGenerator topologyGenerator = new TopologyGenerator();
          topologyGenerator.setArea(parser.length, parser.length);
          topologyGenerator.setOneHopRadius(parser.RADIUS);
          topologyGenerator.setSize(NUM_SERVER);
          topologyGenerator.build(cpath, parser.topology_type, (int) parser.eventList.get(0)[2]);
          System.out.println("Saved topo to " + cpath);
        }
        else {
          System.out.println("Didn't generate topo bc failedNode was "+String.valueOf(failedNode));
        }
      }
      else {
        System.out.println("Didn't generate topo bc eventList.size() was "+String.valueOf(parser.eventList.size()));
      }
    }
    
    // System.out.println("About to init serviceFactory");
    eventList = eventServiceFactory.init(cpath, parser.eventList, parser.optimize_route, parser.routing_path);
    // System.out.println("Finished init serviceFactory");
    if (parser.mobile_percentage > 0) {
      cpath = root + parser.coordinate_path.substring(0, parser.coordinate_path.lastIndexOf('.')) + "_moved_" + runNum + ".txt";
      eventServiceFactory.moveServers(parser.mobile_percentage, parser.mobile_distance, cpath);
    }
    
    List<Server> all_servers = new ArrayList<>();

    FileWriter mFile = new FileWriter(parser.membership_path+"memlist.csv");

    // System.out.println("Just created mFile");



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

    if (parser.use_churn) {
      insertChurnTrace(parser,all_servers);
      // System.out.println("About to enter event loop");
      File file = new File(parser.membership_path+"flag");
      file.createNewFile();
      Files.setPosixFilePermissions(file.toPath(), 
        EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE));
      // Runtime.getRuntime().exec("touch "+parser.membership_path+"flag");
      System.out.println("Created flag");
    }

    long last_ping_time = 0;


    while (current_time < END_TIME) {
      long next_ping_time = last_ping_time + ROUND_PERIOD_MS;
      // System.out.println("in event loop with current_time = " + String.valueOf(current_time));

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
                eventServiceFactory.removeServer(server.id);
                break;
              case 1:
                server.restart(current_time);
                eventServiceFactory.addServer(server.id);
                break;
              case 2:
                server.shutdown(current_time);
                eventServiceFactory.removeServer(server.id);
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
      // TimeUnit.MILLISECONDS.sleep(200);

      // Write membership stats to file
      int rd_num = (int) last_ping_time/ROUND_PERIOD_MS;


      // if (rd_num%10 == 0) {
      //   System.out.println("Completed round "+String.valueOf(rd_num));
      // }


      // System.out.println("Size of all_servers is " + String.valueOf(all_servers.size()) + " at round " + String.valueOf(rd_num));
      for (Server server: all_servers) {
        // System.out.println("Calling writemembership on server ");
        server.writeMembership(parser.membership_path,rd_num,NUM_SERVER,(int) current_time/ROUND_PERIOD_MS);
      }
      if (rd_num%10000==0){
        System.out.println("reached round " + String.valueOf(rd_num));
      }
    }
    statsRecorder.conclude();
    // statsRecorder.print(parser.VERBOSE);
    statsRecorder.toFile(parser.VERBOSE);
  }

  private static void freshServerQueue(List<Server> all_servers) {
    serversWithPendingEvents.clear();
    for (Server server : all_servers) {
      if (server.has_pending_events && !server.disabled){ serversWithPendingEvents.add(server); }
    }
  }

  public static void main(String[] args) throws Exception {
    String config_name = "config.json";
    ConfigParser parser = new ConfigParser();
    if (args.length > 0) {
      config_name = args[0];
    }
    LOG.log(Level.FINE, "Parse configuration file");
    if (!parser.parse(config_name)){
      LOG.log(Level.SEVERE, "Failed to parse configuration file");
      return;
    }

    NUM_RUN = parser.NUM_RUN;
    NUM_SERVER = parser.NUM_SERVER;
    END_TIME = parser.END_TIME;
    BASE_TIME = parser.BASE_TIME;
    PING_RTT = parser.PING_RTT;
    ROUND_PERIOD_MS = parser.ROUND_PERIOD_MS;
    POWERK = parser.POWERK;

    for (int i = 0; i < NUM_RUN; ++i) {
      // System.out.println("run number: " + (i + 1));
      run(parser, i + 1);
    }
  }  
}
