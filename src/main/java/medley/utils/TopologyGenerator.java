package medley.utils;

import com.sun.tools.javac.util.Pair;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import medley.simulator.Id;
import medley.simulator.Server;


public class TopologyGenerator {
  private static final Logger LOG = Logger.getLogger(Server.class.getName());
  public double MIN_DIST = 0.1;

  int size;
  double area_length = 15.0;
  double area_width = 15.0;
  double one_hop_radius = 4.0;
  private boolean optimize_route = false;
  private boolean server_moved = false;
  LinkedHashMap<Id, Pair<Double, Double>> locationMap;
  LinkedHashMap<Id, Pair<Double, Double>> originalLocations;
  LinkedHashMap<Id, Pair<Double, Double>> runningLocations;
  HashMap<Id, HashMap<Id, Double>> distanceMap;  // Map of physical distance
  HashMap<Id, HashMap<Id, Double>> hopDistMap;   // Map of hop distance
  HashMap<Id, HashMap<Id, Double>> hopNumMap;   // Map of hop number
  HashMap<Id, HashMap<Id, Id>> routingTable;     // Map of routingTable
  HashMap<Id, HashMap<Id, Id>> runningRoutingTable;
  ArrayList<double[]> rooms;

  // initialize cluster boundary: x_min, x_max, y_min, y_max, num_nodes
  double[] game_room = new double[]{0, 6, 2, 7, 12};
  double[] living_room = new double[]{7, 15, 0, 9, 19};
  double[] master_room = new double[]{0, 6, 9, 15, 13};
  double[] bedroom_1 = new double[]{7, 10.5, 11, 15, 10};
  double[] bedroom_2 = new double[]{11.5, 15, 11, 15, 10};
  int[] room_bound = new int[]{0, 12, 31, 44, 54, 64};

  public TopologyGenerator() {
    this.locationMap = new LinkedHashMap<>();
    this.runningLocations = new LinkedHashMap<>();
    this.distanceMap = new HashMap<>();
    this.hopDistMap = new HashMap<>();
    this.routingTable = new HashMap<>();
    this.runningRoutingTable = new HashMap<>();
    this.originalLocations = new LinkedHashMap<>();
    this.hopNumMap = new HashMap<>();
    rooms = new ArrayList<>();
    rooms.add(game_room);
    rooms.add(living_room);
    rooms.add(master_room);
    rooms.add(bedroom_1);
    rooms.add(bedroom_2);
  }

  public void setSize(int n) {
    this.size = n;
  }

  public void setArea(double length, double width) {
    this.area_length = length;
    this.area_width = width;
  }

  public void setOneHopRadius(double radius) {
    this.one_hop_radius = radius;
  }

  public List<long[]> generate_failure(List<long[]> eventList) {
    if (eventList.size() == 1) {
      long fail_time = eventList.get(0)[1];
      long mode = eventList.get(0)[2];
      if (mode == 3L) {
        // generate event list for domain failure
        List<long[]> newEventList = new ArrayList<>();
        int cluster_num = (int) eventList.get(0)[0] - 1;
        if (cluster_num < 0 || cluster_num > 4) {
          System.out.println("Cluster number invalid");
          System.exit(1);
        }
        for (int i = room_bound[cluster_num]; i < room_bound[cluster_num + 1]; i++) {
          long[] params = new long[3];
          params[0] = i;
          params[1] = fail_time;
          params[2] = 0L;
          newEventList.add(params);
        }
        return newEventList;
      } else if (mode == 4L) {
        // randomly fail multiple nodes simultaneously
        LinkedHashMap<Id, Pair<Double, Double>> original_map = copy_map(locationMap);
        long num_fails = eventList.get(0)[0];
        List<long[]> newEventList;
        do {
          locationMap = copy_map(original_map);
          newEventList = new ArrayList<>();
          int[] fails = new Random().ints(0, size).distinct().limit(num_fails).toArray();
          for (int fail : fails) {
            final Id id = Id.newBuilder()
                    .setHostname("192.168.0." + fail)
                    .setPort(fail)
                    .setTs(0)
                    .build();
            locationMap.remove(id);
            long[] params = new long[3];
            params[0] = fail;
            params[1] = fail_time;
            params[2] = 0L;
            newEventList.add(params);
          }
        } while (!shortest_path());
        locationMap = copy_map(original_map);
        shortest_path();
        return newEventList;
      }
    }
    return new ArrayList<>(eventList);
  }

  public HashMap<Id, HashMap<Id, Id>> getRoutingTable() {
    // return routingTable;
    return server_moved ? runningRoutingTable : routingTable;
  }

  public HashMap<Id, HashMap<Id, Double>> getDistanceMap() {
    return distanceMap;
  }

  public HashMap<Id, HashMap<Id, Double>> getHopDistMap() {
    return hopDistMap;
  }

  public HashMap<Id, HashMap<Id, Double>> getHopNumMap() {
    return hopNumMap;
  }

  public HashMap<String, Double> getPhysicalDistances(Id id) {
    HashMap<String, Double> distances = new HashMap<>();
    if (!distanceMap.containsKey(id)) {
      LOG.log(Level.SEVERE, "Getting physical distances from node not prepared.");
    } else {
      distanceMap.get(id).forEach((k, v) -> distances.put(getAddrfromId(k), v));
    }
    return distances;
  }

  public HashMap<String, Double> getHopDistances(Id id) {
    HashMap<String, Double> distances = new HashMap<>();
    if (!hopDistMap.containsKey(id)) {
      LOG.log(Level.SEVERE, "Getting physical distances from node not prepared.");
    } else {
      hopDistMap.get(id).forEach((k, v) -> distances.put(getAddrfromId(k), v));
    }
    return distances;
  }

  private LinkedHashMap<Id, Pair<Double, Double>> buildRandom() {
    LinkedHashMap<Id, Pair<Double, Double>> locations = new LinkedHashMap<>();
    Random rand = new Random();
    for (int i = 0; i < this.size; ++i) {
      final Id id = Id.newBuilder()
              .setHostname("192.168.0." + i)
              .setPort(i)
              .setTs(0)
              .build();
      double width = rand.nextDouble() * this.area_width;
      double length = rand.nextDouble() * this.area_length;
      locations.put(id, new Pair<>(width, length));
    }

    return locations;
  }

  private LinkedHashMap<Id, Pair<Double, Double>> buildCluster() {
    LinkedHashMap<Id, Pair<Double, Double>> locations = new LinkedHashMap<>();
    if (area_width != 15 || area_length != 15) {
      System.out.println("Build cluster only implemented for topology with size 15x15");
      System.exit(0);
    }

    Random rand = new Random();
    int id_count = 0;
    for (double[] room : rooms) {
      int num_nodes = (int) room[4];
      // System.out.println("Num_nodes in room is "+String.valueOf(num_nodes));
      for (int i = 0; i < num_nodes; i++) {
        final Id id = Id.newBuilder()
                .setHostname("192.168.0." + id_count)
                .setPort(id_count)
                .setTs(0)
                .build();
        id_count++;
        double x = rand.nextDouble() * (room[1] - room[0]) + room[0];
        double y = rand.nextDouble() * (room[3] - room[2]) + room[2];
        locations.put(id, new Pair<>(x, y));
      }
    }
    // System.out.println(locations);
    return locations;
  }

  private LinkedHashMap<Id, Pair<Double, Double>> buildUnlucky() {
    // TODO: temporary generation method: unlucky nodes are evenly distributed at corners/borders while others form a cluster at the center
    // currently only support one unlucky node

    LinkedHashMap<Id, Pair<Double, Double>> locations = new LinkedHashMap<>();
    // server 0 at lower left corner
    Id id0 = Id.newBuilder()
            .setHostname("192.168.0." + 0)
            .setPort(0)
            .setTs(0)
            .build();
    locations.put(id0, new Pair<>(0.0, 0.0));

    // other servers are clustered at (5,5) with std 2
    Random rand = new Random();
    for (int i = 1; i < this.size; ++i) {
      final Id id = Id.newBuilder()
              .setHostname("192.168.0." + i)
              .setPort(i)
              .setTs(0)
              .build();
      double width = -1.0;
      double length = -1.0;
      while (width < 0.0 || width > this.area_width) {
        width = 2 * rand.nextGaussian() + 5.0;
      }
      while (length < 0.0 || length > this.area_length) {
        length = 2 * rand.nextGaussian() + 5.0;
      }
      locations.put(id, new Pair<>(width, length));
    }

    return locations;
  }

  public void set_locations(String path, boolean optimize_route, String rpath) {
    // read server location information from coordinate file
    try {
      // System.out.println("top of set_locations with path = " + path);
      BufferedReader reader = new BufferedReader(new FileReader(path));
//      String line = reader.readLine();
//      String[] list = line.split(",");
//      area_width = Double.parseDouble(list[0]);
//      area_length = Double.parseDouble(list[1]);
//      one_hop_radius = Double.parseDouble(list[2]);
      int i = 0;

      String line = reader.readLine();
      String[] list;
      // System.out.println("Entering while loop in set_locations");
      while (line != null) {
        list = line.split(",");
        Id id = Id.newBuilder()
                .setHostname("192.168.0." + i)
                .setPort(i)
                .setTs(0L)
                .build();
        locationMap.put(id, new Pair<>(Double.parseDouble(list[0]), Double.parseDouble(list[1])));
        runningLocations.put(id, new Pair<>(Double.parseDouble(list[0]), Double.parseDouble(list[1])));
        originalLocations.put(id, new Pair<>(Double.parseDouble(list[0]), Double.parseDouble(list[1])));
        line = reader.readLine();
        i++;
      }
      // System.out.println("Exited while loop in set_locations");
      size = i;
      // System.out.println("Size is "+String.valueOf(size));
    } catch (Exception e) {
      System.out.println("set_locations exception was " + e);
      e.printStackTrace();
    }
    if (optimize_route) {
      parse_map(rpath);
    } else {
      shortest_path();
    }
  }

  public void remove_server(Id id) {
    locationMap.remove(id);
    runningLocations.remove(id);
    if (optimize_route) {
      update_failure(id);
    } else {
      shortest_path();
    }
    if (server_moved) {
      update_running_routing();
    }
  }

  public void add_server(Id id) {
    Id tempId = Id.newBuilder()
            .setHostname(id.getHostname())
            .setPort(id.getPort())
            .setTs(0)
            .build();
    locationMap.put(id, originalLocations.get(tempId));
    shortest_path();
  }

  private LinkedHashMap<Id, Pair<Double, Double>> copy_map(LinkedHashMap<Id, Pair<Double, Double>> old_map) {
    LinkedHashMap<Id, Pair<Double, Double>> new_map = new LinkedHashMap<>();
    for (Map.Entry<Id, Pair<Double, Double>> entry : old_map.entrySet()) {
      new_map.put(entry.getKey(), entry.getValue());
    }
    return new_map;
  }

  private boolean check_partition(int runType) {
    if (!shortest_path()) return false;
    for (Id from : distanceMap.keySet()) {
      for (Id to : distanceMap.keySet()) {
        if (from.equals(to)) continue;
        if (distanceMap.get(from).get(to) < MIN_DIST) return false;
      }
    }
    if (runType == 0) { // single failure
      // check that the topology does not have partition if any one of the node fails
      LinkedHashMap<Id, Pair<Double, Double>> original_map = copy_map(locationMap);
      for (Id remove_id : original_map.keySet()) {
        locationMap.remove(remove_id);
        if (!shortest_path()) return false;
        locationMap = copy_map(original_map);
      }
    } else if (runType == 3) { // domain failure
      // check that the topology does not have partition if all nodes in one room fail
      LinkedHashMap<Id, Pair<Double, Double>> original_map = copy_map(locationMap);
      for (int i = 0; i < room_bound.length-1; i++) {
        for (int j = room_bound[i]; j < room_bound[i+1]; j++) {
          final Id remove_id = Id.newBuilder()
                  .setHostname("192.168.0." + j)
                  .setPort(j)
                  .setTs(0)
                  .build();
          locationMap.remove(remove_id);
        }
        if (!shortest_path()) return false;
        locationMap = copy_map(original_map);
      }
    }
    return true;
  }

  public void move_servers(double percentage, double distance, String cpath) {
    server_moved = true;
    int num_move = (int) (runningLocations.size() * percentage);
    LinkedHashMap<Id, Pair<Double, Double>> originalMap = copy_map(runningLocations);
    List<Id> ids = new ArrayList<>(runningLocations.keySet());
    Random rand = new Random();
    do {
      runningLocations = copy_map(originalMap);
      HashSet<Integer> move_set = new HashSet<>();
      for (int i = 0; i < num_move; i++) {
        int chosen_server;
        do {
          chosen_server = rand.nextInt(runningLocations.size());
        } while (move_set.contains(chosen_server));
        move_set.add(chosen_server);
        double x = runningLocations.get(ids.get(chosen_server)).fst;
        double y = runningLocations.get(ids.get(chosen_server)).snd;
        double new_x = Math.max(0, x - distance) + (Math.min(area_width, x + distance) - Math.max(0, x - distance)) * rand.nextDouble();
        double new_y = Math.max(0, y - distance) + (Math.min(area_length, y + distance) - Math.max(0, y - distance)) * rand.nextDouble();
        runningLocations.put(ids.get(chosen_server), new Pair<>(new_x, new_y));
      }
    }
    while (!update_running_routing());

    // save coordinates to file
    try {
      FileWriter cfile = new FileWriter(cpath);
      BufferedWriter output = new BufferedWriter(cfile);
      StringBuilder str = new StringBuilder();
      // str.append(area_width).append(",").append(area_length).append(",").append(one_hop_radius).append("\n");
      for (int i = 0; i < size; i++) {
        final Id id = Id.newBuilder()
                .setHostname("192.168.0." + i)
                .setPort(i)
                .setTs(0)
                .build();
        Pair<Double, Double> c = runningLocations.get(id);
        str.append(c.fst).append(",").append(c.snd).append("\n");
      }
      output.write(str.toString());
      output.close();
    } catch (Exception e) {
      e.printStackTrace();
    }

//    for (Id id : locationMap.keySet()) {
//      if (!Objects.equals(locationMap.get(id).fst, runningLocations.get(id).fst) || !Objects.equals(locationMap.get(id).snd, runningLocations.get(id).snd)) {
//        System.out.println(locationMap.get(id));
//        System.out.println(runningLocations.get(id));
//        System.out.println("");
//      }
//    }
  }

  public void parse_map(String path) {
    try {
      BufferedReader reader = new BufferedReader(new FileReader(path));
      String line = reader.readLine();
      String[] list;
      String key = "";
      while (line != null) {
        switch (line) {
          case "dist_map:":
          case "hop_dist_map:":
          case "route_table:":
          case "hop_num_map:":
            key = line;
            line = reader.readLine();
            continue;
          default:
            break;
        }
        list = line.split(",");
        int a = Integer.parseInt(list[0]);
        int b = Integer.parseInt(list[1]);
        Id from = Id.newBuilder()
                .setHostname("192.168.0." + a)
                .setPort(a)
                .setTs(0L)
                .build();
        Id to = Id.newBuilder()
                .setHostname("192.168.0." + b)
                .setPort(b)
                .setTs(0L)
                .build();
        switch (key) {
          case "dist_map:":
            if (!distanceMap.containsKey(from)) {
              distanceMap.put(from, new HashMap<>());
            }
            distanceMap.get(from).put(to, Double.parseDouble(list[2]));
            break;
          case "hop_dist_map:":
            if (!hopDistMap.containsKey(from)) {
              hopDistMap.put(from, new HashMap<>());
            }
            hopDistMap.get(from).put(to, Double.parseDouble(list[2]));
            break;
          case "route_table:":
            if (!routingTable.containsKey(from)) {
              routingTable.put(from, new HashMap<>());
            }
            int n = Integer.parseInt(list[2]);
            Id nextHop = Id.newBuilder()
                    .setHostname("192.168.0." + n)
                    .setPort(n)
                    .setTs(0L)
                    .build();
            routingTable.get(from).put(to, nextHop);
            break;
          case "hop_num_map:":
            if (!hopNumMap.containsKey(from)) {
              hopNumMap.put(from, new HashMap<>());
            }
            hopNumMap.get(from).put(to, Double.parseDouble(list[2]));
            break;
        }
        line = reader.readLine();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    optimize_route = true;
  }

  private boolean update_running_routing() {
    HashMap<Id, HashMap<Id, Double>> tempMap = new HashMap<>();
    for (Id from : runningLocations.keySet()) {
      tempMap.put(from, new HashMap<>());
      runningRoutingTable.put(from, new HashMap<>());
    }
    for (Id from : runningLocations.keySet()) {
      for (Id to : runningLocations.keySet()) {
        double distance = GetPhysicalDist(
                runningLocations.get(from).fst, runningLocations.get(from).snd,
                runningLocations.get(to).fst, runningLocations.get(to).snd);
        if (distance > one_hop_radius) {
          tempMap.get(from).put(to, Double.MAX_VALUE);
          tempMap.get(to).put(from, Double.MAX_VALUE);
        } else {
          tempMap.get(from).put(to, distance);
          runningRoutingTable.get(from).put(to, to);
          tempMap.get(to).put(from, distance);
          runningRoutingTable.get(to).put(from, from);
        }
      }
    }
    for (Id middle : runningLocations.keySet()) {
      for (Id from : runningLocations.keySet()) {
        for (Id to : runningLocations.keySet()) {
          if (tempMap.get(from).get(middle) + tempMap.get(middle).get(to)
                  < tempMap.get(from).get(to)) {
            tempMap.get(from).put(to, tempMap.get(from).get(middle) + tempMap.get(middle).get(to));
            runningRoutingTable.get(from).put(to, runningRoutingTable.get(from).get(middle));
            tempMap.get(to).put(from, tempMap.get(from).get(middle) + tempMap.get(middle).get(to));
            runningRoutingTable.get(to).put(from, runningRoutingTable.get(to).get(middle));
          }
        }
      }
    }
    for (Id from : runningLocations.keySet()) {
      for (Id to : runningLocations.keySet()) {
        if (!runningRoutingTable.get(from).containsKey(to)) {
          return false;
        }
      }
    }
    return true;
  }

  public void update_failure(Id failId) {
    // For nodes whose neighbor is failId, update their info
    // TODO: not compatible with moving servers
    // find the neighbors
    List<Id> neighbors = new ArrayList<>();
    for (Id id : routingTable.keySet()) {
      if (Objects.equals(routingTable.get(id).get(failId), failId) && !Objects.equals(id, failId)) {
        neighbors.add(id);
      }
    }

    // remove failId info from the three maps
    hopDistMap.remove(failId);
    routingTable.remove(failId);
    hopNumMap.remove(failId);
    for (Id id : distanceMap.keySet()) {
      if (Objects.equals(id, failId)) continue;
      hopDistMap.get(id).remove(failId);
      routingTable.get(id).remove(failId);
      hopNumMap.get(id).remove(failId);
    }

    // recalculate the best routing for the neighbor nodes
    for (Id from : neighbors) {
      for (Id to : routingTable.get(from).keySet()) {
        if (Objects.equals(routingTable.get(from).get(to), failId)) {
          // original next hop is the failed node
          double dist = Double.MAX_VALUE;
          Id nextHop = null;
          for (Id middle : routingTable.get(from).keySet()) {
            if (!Objects.equals(routingTable.get(from).get(middle), middle)) continue;
            if (distanceMap.get(from).get(middle) + hopDistMap.get(middle).get(to) < dist) {
              // update routing info
              dist = distanceMap.get(from).get(middle) + hopDistMap.get(middle).get(to);
              nextHop = middle;
            }
          }
          routingTable.get(from).put(to, nextHop);
          hopDistMap.get(from).put(to, dist);
          hopNumMap.get(from).put(to, hopNumMap.get(nextHop).get(to) + 1);
        }
      }
    }
  }

  private boolean shortest_path() {
    // Generate physical distance
    for (Id from : this.locationMap.keySet()) {
      for (Id to : this.locationMap.keySet()) {
        if (from.equals(to)) continue;
        double physical_dist = GetPhysicalDist(
                locationMap.get(from).fst, locationMap.get(from).snd,
                locationMap.get(to).fst, locationMap.get(to).snd);

        if (!distanceMap.containsKey(from)) {
          distanceMap.put(from, new HashMap<>());
        }
        if (!distanceMap.containsKey(to)) {
          distanceMap.put(to, new HashMap<>());
        }

        distanceMap.get(from).put(to, physical_dist);
        distanceMap.get(to).put(from, physical_dist);
      }
    }

    // Generate hop distance
    // Step 0: Initialization
    for (Id from : this.locationMap.keySet()) {
      hopDistMap.put(from, new HashMap<>());
      routingTable.put(from, new HashMap<>());
      hopNumMap.put(from, new HashMap<>());
    }
    // System.out.println("Step 0 done");
    // Step 1: Generate distances between 1-hop nodes.
    //         Direct distance between two nodes is < one_hop_radius.
    for (Id from : this.locationMap.keySet()) {
      for (Id to : this.locationMap.keySet()) {
        double distance = distanceMap.get(from).getOrDefault(to, 0.0);
        if (distance > one_hop_radius) {
          hopDistMap.get(from).put(to, Double.MAX_VALUE);
          hopDistMap.get(to).put(from, Double.MAX_VALUE);
        } else {
          hopDistMap.get(from).put(to, distance);
          routingTable.get(from).put(to, to);
          hopDistMap.get(to).put(from, distance);
          routingTable.get(to).put(from, from);
        }
      }
    }
    // System.out.println("Step 1 done");

    // Step 2: Generate shortest distances between every two nodes (Floyd)
    for (Id middle : this.locationMap.keySet()) {
      // Pick all vertices as source one by one
      for (Id from : this.locationMap.keySet()) {
        // Pick all vertices as destination for the
        // above picked source
        for (Id to : this.locationMap.keySet()) {
          // If vertex middle is on the shortest path from
          // from to to, then update the value of distance
          if (hopDistMap.get(from).get(middle) + hopDistMap.get(middle).get(to)
                  < hopDistMap.get(from).get(to)) {
            hopDistMap.get(from).put(to, hopDistMap.get(from).get(middle) + hopDistMap.get(middle).get(to));
            routingTable.get(from).put(to, routingTable.get(from).get(middle));
            hopDistMap.get(to).put(from, hopDistMap.get(from).get(middle) + hopDistMap.get(middle).get(to));
            routingTable.get(to).put(from, routingTable.get(to).get(middle));
          }
        }
      }
    }
    // System.out.println("Step 2 done");

    // Step 3: check network partition
    for (Id from : this.locationMap.keySet()) {
      // Pick all vertices as destination for the
      // above picked source
      for (Id to : this.locationMap.keySet()) {
        if (!routingTable.get(from).containsKey(to)) {
          return false;
        }
      }
    }
    
    // System.out.println("Step 3 done");
    // Step 4: generate hop number map
    for (Id from : locationMap.keySet()) {
      for (Id to : locationMap.keySet()) {
        if (from.equals(to)) {
          hopNumMap.get(from).put(to, 0.0);
          continue;
        }
        Id nextHop = routingTable.get(from).get(to);
        double hop = 1.0;
        while (!nextHop.equals(to)) {
          nextHop = routingTable.get(nextHop).get(to);
          hop += 1.0;
        }
        hopNumMap.get(from).put(to, hop);
      }
    }
    // System.out.println("Step 4 done");

    return true;
  }

  public void build(String cpath, String topoType, int runType) {
    // Generate different types of topologies
    int retry = 0;
    do {
      // System.out.println(retry);
      retry++;
      switch (topoType) {
        case "unlucky":
          locationMap = buildUnlucky();
          break;
        case "random":
          locationMap = buildRandom();
          break;
        case "cluster":
          locationMap = buildCluster();
          break;
        default:
          System.out.println("No such topology generator.");
          System.exit(0);
      }
    } while (!check_partition(runType));

    // save coordinates to file
    try {
      FileWriter cfile = new FileWriter(cpath);
      BufferedWriter output = new BufferedWriter(cfile);
      StringBuilder str = new StringBuilder();
      // str.append(area_width).append(",").append(area_length).append(",").append(one_hop_radius).append("\n");
      // System.out.println("Upper bound of loop size is "+String.valueOf(size));
      for (int i = 0; i < size; i++) {
        final Id id = Id.newBuilder()
                .setHostname("192.168.0." + i)
                .setPort(i)
                .setTs(0)
                .build();
        Pair<Double, Double> c = locationMap.get(id);
        if (c == null) {
          System.out.println("Couldn't find id " + String.valueOf(i)+" in locationMap");
        }
        str.append(c.fst).append(",").append(c.snd).append("\n");
      }
      output.write(str.toString());
      output.close();
    } catch (Exception e) {
      e.printStackTrace();
    }

    // Save distance file
//    try {
//      FileWriter file = new FileWriter(physicalDisPath);
//      BufferedWriter output = new BufferedWriter(file);
//
//      StringBuilder str = new StringBuilder();
//      str.append(this.size).append("\n");
//
//      for (Id id : locationMap.keySet()) {
//        str.append(getAddrfromId(id)).append("\n");
//      }
//      for (Id i : locationMap.keySet()) {
//        for (Id j : locationMap.keySet()) {
//          if (i.equals(j))
//            continue;
//          double dist = (double) Math.round(distanceMap.get(i).get(j) * 100d) / 100d;
//          str.append(i.getPort()).append(",").append(j.getPort()).append(",").append(dist).append("\n");
//        }
//      }
//
//      output.write(str.toString());
//      output.close();
//    } catch (IOException e) {
//      LOG.log(Level.SEVERE, "Fail to save topology to file", e);
//    }

    // generate shortest paths
    shortest_path();

    // Save hop distance and routine table
    // Save distance file
    // String hopDisPath = saveDir + "/HopDistance.txt";
//    try {
//      FileWriter file = new FileWriter(hopDisPath);
//      BufferedWriter output = new BufferedWriter(file);
//
//      StringBuilder str = new StringBuilder();
//      str.append(this.size).append("\n");
//
//      for (Id id : locationMap.keySet()) {
//        str.append(getAddrfromId(id)).append("\n");
//      }
//      for (Id i : locationMap.keySet()) {
//        for (Id j : locationMap.keySet()) {
//          if (i.equals(j))
//            continue;
//          double dist = (double) Math.round(hopDistMap.get(i).get(j) * 100d) / 100d;
//          int route = routingTable.get(i).get(j).getPort();
//          System.out.printf("from %d to %d, route: %d, dist %f\n", i.getPort(), j.getPort(), route, dist);
//          str.append(i.getPort()).append(",").append(j.getPort()).append(",").append(dist).append(",").append(route).append("\n");
//        }
//      }
//
//      output.write(str.toString());
//      output.close();
//    } catch (IOException e) {
//      LOG.log(Level.SEVERE, "Fail to save topology to file", e);
//    }

  }

  private double GetPhysicalDist(Double x1, Double y1, Double x2, Double y2) {
    return Math.sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1));
  }

  public String getAddrfromId(Id single_id) { // Fetch Id to String [id]
    return single_id.getHostname() + ":" + single_id.getPort();
  }
}
