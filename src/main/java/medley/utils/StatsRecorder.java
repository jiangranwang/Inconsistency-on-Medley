package medley.utils;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.sun.tools.javac.util.Pair;

import medley.simulator.Id;
import medley.simulator.Server;
import medley.simulator.Type;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class StatsRecorder{
  List<Id> alive_servers;
  private static final Logger LOG = Logger.getLogger(StatsRecorder.class.getName());
  private final ConfigParser parser;
  private final int runNum;
  private final HashMap<Id, HashMap<Id, Pair<Id, Long>>> deathTime;
  private final HashMap<Id, Integer> msgNum;
  private final HashMap<Id, Integer> indMsgNum;
  private final HashMap<Id, HashMap<Id, Integer>> pairWiseMsgNum;
  private final HashMap<Id, HashMap<Id, Integer>> endEndMsgNum;
  private final HashMap<Id, HashMap<Id, Integer>> falsePositiveStatus;
  private final HashMap<Id, List<Pair<Id, Long>>> failDetectionTime;
  private final List<long[]> falsePositiveRecord;
  private final List<Double> firstDetectDist;
  private int currFPNum = 0;
  private long ttlFPTime = 0;
  private long FPStartTime = -1;
  private int falsePositives = 0;
  private int falseNegatives = 0;
  private int failedNode = -1;
  private long firstDetectTime = -1;

  public int ttlMsgNum = 0;
  public int ttlIndMsgNum = 0;
  public int ttlPingNum = 0;
  public int totalPassive = 0;
  public int ttlPingToFailedNode = 0;
  // 0-PING, 1-JOIN, 2-JOIN_ACK, 3-ACK, 4-IND_PING, 5-IND_PING_ACK,
  // 6-TERMINATE, 7-CRASH, 8-UNLUCKY, 9-REPORT_ALIVE,
  // 10-PING caused by direct ping, 11-PING cased by indirect ping
  public int[] typeWiseMsgNum = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
  public long firstFailDetectTime = -1;

  // For active feedback
  private final HashMap<Id, ArrayList<Long>> activeUnlucky = new HashMap<>();

  // For passive feedback
  private final HashMap<Id, HashMap<Id, Integer>> passiveUnlucky = new HashMap<>();
  private final HashMap<Id, HashMap<Id, Integer>> passiveTarget = new HashMap<>();


  public StatsRecorder(List<Server> all_servers, ConfigParser parser, int runNum){
    alive_servers = new ArrayList<>();
    this.parser = parser;
    this.runNum = runNum;
    deathTime = new HashMap<>();
    msgNum = new HashMap<>();
    indMsgNum = new HashMap<>();
    pairWiseMsgNum = new HashMap<>();
    endEndMsgNum = new HashMap<>();
    failDetectionTime = new HashMap<>();
    falsePositiveRecord = new ArrayList<>();
    falsePositiveStatus = new HashMap<>();
    firstDetectDist = new ArrayList<>();
    for (Server server : all_servers){
      alive_servers.add(server.id);
      deathTime.put(server.id, null);
      msgNum.put(server.id, 0);
      indMsgNum.put(server.id, 0);
      HashMap<Id, Integer> tempMap = new HashMap<>();
      HashMap<Id, Integer> tempPMap = new HashMap<>();
      HashMap<Id, Integer> tempBMap = new HashMap<>();
      for (Server targetServer : all_servers){
        // if (!server.id.equals(targetServer.id)){
        tempMap.put(targetServer.id, 0);
        tempPMap.put(targetServer.id, 0);
        tempBMap.put(targetServer.id, 0);
        // }
      }
      pairWiseMsgNum.put(server.id, tempPMap);
      endEndMsgNum.put(server.id, tempMap);
      falsePositiveStatus.put(server.id, tempBMap);
    }
    if (parser.eventList.size() == 1 && (int) parser.eventList.get(0)[2] == 0L) {
      failedNode = (int) parser.eventList.get(0)[0];
    }
  }

  public void incrementMsgNum(Id id, Type e, Id init_id, Id target_id){
    msgNum.put(id, msgNum.get(id) + 1);
      if (e == Type.PING) {
        typeWiseMsgNum[0]++;
        if (target_id.getPort() == failedNode) {
          ttlPingToFailedNode++;
        }
        if (id.equals(init_id))
          typeWiseMsgNum[10]++;  // PING caused by direct ping
        else
          typeWiseMsgNum[11]++;  // PING initiates due to indirect ping
      } else if (e == Type.JOIN)
        typeWiseMsgNum[1]++; 
      else if (e == Type.JOIN_ACK)
        typeWiseMsgNum[2]++;
      else if (e == Type.ACK)
        typeWiseMsgNum[3]++;
      else if (e == Type.IND_PING)
        typeWiseMsgNum[4]++;
      else if (e == Type.IND_PING_ACK)
        typeWiseMsgNum[5]++;
      else if (e == Type.TERMINATE)
        typeWiseMsgNum[6]++;
      else if (e == Type.CRASH)
        typeWiseMsgNum[7]++;
      else if (e == Type.UNLUCKY)
        typeWiseMsgNum[8]++;
      else if (e == Type.REPORT_ALIVE)
        typeWiseMsgNum[9]++;
  }

  public void incrementIndMsgNum(Id id){
    indMsgNum.put(id, indMsgNum.get(id) + 1);
  }

  public void incrementPairWiseMsgNum(Id source_id, Id target_id){
    if (source_id != null && target_id != null && !Objects.equals(source_id.getPort(), target_id.getPort())){
      HashMap<Id, Integer> tempMap = pairWiseMsgNum.get(source_id);
      tempMap.put(target_id, tempMap.get(target_id)+1);
      pairWiseMsgNum.put(source_id, tempMap);
    }
  }

  public void incrementEndEndMsgNum(Id source_id, Id target_id){
    if (source_id != null && target_id != null && !Objects.equals(source_id.getPort(), target_id.getPort())){
      HashMap<Id, Integer> tempMap = endEndMsgNum.get(source_id);
      tempMap.put(target_id, tempMap.get(target_id)+1);
      endEndMsgNum.put(source_id, tempMap);
    }
  }

  public void setDeath(Id id, long time){
    HashMap<Id, Pair<Id, Long>> timeRecord = new HashMap<>();
    for (Id server : alive_servers){
      if (Objects.equals(server, id))
        timeRecord.put(id, new Pair<>(id, time));
      else
        timeRecord.put(server, null);
    }
    alive_servers.remove(id);
    deathTime.put(id, timeRecord);

    int tempNum = 0;
    for (Map.Entry<Id, HashMap<Id, Integer>> entry : falsePositiveStatus.entrySet()) {
      if (!entry.getKey().equals(id)){
        if (entry.getValue().get(id) == 1) tempNum++;
        entry.getValue().remove(id);
      }
    }
    currFPNum -= tempNum;
    tempNum = 0;
    for (Map.Entry<Id, Integer> entry : falsePositiveStatus.get(id).entrySet()) {
      if (!entry.getKey().equals(id)){
        if (entry.getValue() == 1) tempNum++;
      }
    }
    falsePositiveStatus.remove(id);
    currFPNum -= tempNum;
    if (currFPNum == 0 && FPStartTime >= 0){
      ttlFPTime += time - FPStartTime;
      FPStartTime = -1;
    }
    LOG.log(Level.INFO, "Port " + id.getPort() + " Death set at " + time);
  }

  public void restart(Id id, long time){
    alive_servers.add(id);
  }

  public void rejoin(Id id, long time){
    alive_servers.add(id);
    deathTime.put(id, null);
    msgNum.put(id, 0);
    indMsgNum.put(id, 0);
    HashMap<Id, Integer> tempMap = new HashMap<>();
    HashMap<Id, Integer> tempBMap = new HashMap<>();
    for (Id targetServer : alive_servers){
      if (targetServer.getPort() != id.getPort()){
        tempMap.put(targetServer, 0);
        tempBMap.put(targetServer, 0);
        HashMap<Id, Integer> targetMap = pairWiseMsgNum.get(targetServer);
        HashMap<Id, Integer> endMap = endEndMsgNum.get(targetServer);
        HashMap<Id, Integer> FPMap = falsePositiveStatus.get(targetServer);
        targetMap.put(id, 0);
        endMap.put(id, 0);
        FPMap.put(id, 0);
        pairWiseMsgNum.put(targetServer, targetMap);
        endEndMsgNum.put(targetServer, endMap);
        falsePositiveStatus.put(targetServer, FPMap);
      }
    }
    pairWiseMsgNum.put(id, tempMap);
    endEndMsgNum.put(id, tempMap);
    falsePositiveStatus.put(id, tempBMap);
  }

  public void recordFail(Id id, Id recorderId, long time, double distance){
    if (id.getPort() == failedNode && (firstDetectTime < 0 || time == firstDetectTime)) {
      firstDetectDist.add(distance);
      firstDetectTime = time;
    }
    HashMap<Id, Pair<Id, Long>> timeRecord = deathTime.get(id);
    if (timeRecord == null){
      falsePositives++;
      long[] temp = new long[3];
      temp[0] = (long)id.getPort();
      temp[1] = (long)recorderId.getPort();
      temp[2] = time;
      falsePositiveRecord.add(temp);
      if (falsePositiveStatus.get(recorderId).get(id) != null && falsePositiveStatus.get(recorderId).get(id) == 0) {
        falsePositiveStatus.get(recorderId).put(id, 1);
        if (++currFPNum == 1) {
          FPStartTime = time;
        }

        int tempNum = 0;
        for (Map.Entry<Id, HashMap<Id, Integer>> entry : falsePositiveStatus.entrySet()) {
          if (!entry.getKey().equals(id)){
            if (entry.getValue().get(id) == 0) return;
            if (entry.getValue().get(id) == 1) tempNum++;
            // System.out.println(id.getPort() + " " + entry.getKey().getPort() + " " + entry.getValue().get(id));
          }
        }

        for (Map.Entry<Id, HashMap<Id, Integer>> entry : falsePositiveStatus.entrySet()){
          if (!entry.getKey().equals(id)){
            entry.getValue().put(id, 0);
          }
        }
        currFPNum -= tempNum;
        if (currFPNum == 0 && FPStartTime >= 0){
          // System.out.println("recordFail: FP ends at " + time);
          ttlFPTime += time - FPStartTime;
          FPStartTime = -1;
        }
      }
      return;
    }
    Pair<Id, Long> death = timeRecord.get(id);
    timeRecord.put(recorderId, new Pair<>(recorderId, time - death.snd));
    LOG.log(Level.INFO, "Fail record at " + time);
    deathTime.put(id, timeRecord);
    if (firstFailDetectTime == -1) firstFailDetectTime = time - death.snd;

    // check if all servers have reported this failure event
    List<Pair<Id, Long>> storeList = new ArrayList<>();
    for (Map.Entry<Id, Pair<Id, Long>> entry : timeRecord.entrySet()){
      if (entry.getValue() == null) return;
      if (Objects.equals(entry.getKey(), id)) continue;
      storeList.add(entry.getValue());
    }
    failDetectionTime.put(id, storeList); // collect the record in a holder
    deathTime.put(id, null); // clear the temporary record
  }

  public void resolveFP(Id id, Id recorderId, long time){
    if (!falsePositiveStatus.containsKey(recorderId)) return;
    HashMap<Id, Integer> tempBMap = falsePositiveStatus.get(recorderId);
    if (!tempBMap.containsKey(id)) return;
    if (tempBMap.get(id) == 1){
      tempBMap.put(id, 2);
      if (--currFPNum == 0 && FPStartTime >= 0){
        // System.out.println("FP ends at " + time);
        ttlFPTime += time - FPStartTime;
        FPStartTime = -1;
      }
      falsePositiveStatus.put(recorderId, tempBMap);
    }
  }

  public void recordActiveUnlucky(Id observer, long detection_time) {
    if (!activeUnlucky.containsKey(observer)) {
      activeUnlucky.put(observer, new ArrayList<>());
    }
    activeUnlucky.get(observer).add(detection_time);
  }

  public void recordPassiveUnlucky(Id observer, Id target) {
    recordToPassiveMap(observer, target, passiveUnlucky);
    recordToPassiveMap(target, observer, passiveTarget);
  }

  private void recordToPassiveMap(Id key, Id value, HashMap<Id, HashMap<Id, Integer>> target_map) {
    if (!target_map.containsKey(key)) {
      target_map.put(key, new HashMap<>());
    }
    if (!target_map.get(key).containsKey(value)) {
      target_map.get(key).put(value, 0);
    }
    int original_res = target_map.get(key).get(value);
    target_map.get(key).put(value, original_res + 1);
  }

  public void conclude(){
    for (Map.Entry<Id, HashMap<Id, Pair<Id, Long>>> entry1 : deathTime.entrySet()){
      conclude(entry1.getKey());
    }
    if (FPStartTime >= 0){
      ttlFPTime += parser.END_TIME - FPStartTime;
    }
  }

  public void conclude(Id id){
    HashMap<Id, Pair<Id, Long>> timeRecord = deathTime.get(id);
    List<Pair<Id, Long>> storeList = new ArrayList<>();
    if (timeRecord == null) return;
    for (Map.Entry<Id, Pair<Id, Long>> entry : timeRecord.entrySet()){
      if (entry.getValue() == null)
        falseNegatives++;
      else {
        if (entry.getKey().getPort() != id.getPort())
        storeList.add(entry.getValue());
      }
    }
    deathTime.put(id, null);
    // if (!storeList.isEmpty())
    failDetectionTime.put(id, storeList);
  }

  public void print(int verbose){
    LOG.log(Level.INFO, "Run #: " + this.runNum);

    // Print average detection time summary
    long ttlFirstTime = 0;
    long ttlLastTime = 0;
    long ttlTime = 0;
    int reportCount = 0;
    for (Id id : failDetectionTime.keySet()){
      List<Pair<Id, Long>> record = failDetectionTime.get(id);
      List<Long> timeRecord = new ArrayList<>();
      for (Pair<Id, Long> temp : record) {
        timeRecord.add(temp.snd);
      }
      Collections.sort(timeRecord);
      for (int i =0; i < timeRecord.size(); i++){
        if (i == 0)
          ttlFirstTime += timeRecord.get(i);
        if (i == timeRecord.size()-1)
          ttlLastTime += timeRecord.get(i);
        ttlTime += timeRecord.get(i);
        reportCount++;
      }
    }
    int avgFirstTime = 0;
    int avgLastTime = 0;
    int avgTime = 0;
    if (failDetectionTime.size() != 0){
      avgFirstTime = (int)ttlFirstTime / failDetectionTime.size();
      avgLastTime = (int)ttlLastTime / failDetectionTime.size();
    }
    if (reportCount != 0)
      avgTime = (int)ttlTime / reportCount;
    LOG.log(Level.INFO, "Average failure detection time: " + avgTime);
    LOG.log(Level.INFO, "Average first failure detection time: " + avgFirstTime);
    LOG.log(Level.INFO, "Average last failure detection time: " + avgLastTime);

    // Print communication cost summary
    LOG.log(Level.INFO, "Total end-to-end message number: " + ttlMsgNum);
    LOG.log(Level.INFO, "  Total end-to-end PING message number: " + typeWiseMsgNum[0]);
    LOG.log(Level.INFO, "      " + typeWiseMsgNum[10] + " direct " + typeWiseMsgNum[11] + " indirect ");
    LOG.log(Level.INFO, "  Total end-to-end JOIN message number: " + typeWiseMsgNum[1]);
    LOG.log(Level.INFO, "  Total end-to-end JOIN_ACK message number: " + typeWiseMsgNum[2]);
    LOG.log(Level.INFO, "  Total end-to-end ACK message number: " + typeWiseMsgNum[3]);
    LOG.log(Level.INFO, "  Total end-to-end IND_PING message number: " + typeWiseMsgNum[4]);
    LOG.log(Level.INFO, "  Total end-to-end IND_PING_ACK message number: " + typeWiseMsgNum[5]);
    LOG.log(Level.INFO, "  Total end-to-end TERMINATE message number: " + typeWiseMsgNum[6]);
    LOG.log(Level.INFO, "  Total end-to-end UNLUCKY message number: " + typeWiseMsgNum[7]);
    LOG.log(Level.INFO, "  Total end-to-end PING message number: " + typeWiseMsgNum[8]);
    LOG.log(Level.INFO, "  Total end-to-end REPORT_ALIVE message number: " + typeWiseMsgNum[9]);
    LOG.log(Level.INFO, "Total node-to-node message number: " + ttlIndMsgNum);

    // Print false positive/negatives summary
    // LOG.log(Level.INFO, "detectionError: " + detectionError);
    LOG.log(Level.INFO, "falsePositives: " + falsePositives);
    double fpRate = (double) ttlFPTime / (double) parser.END_TIME * 100.0;
    if (verbose >= 1) LOG.log(Level.INFO, "FP rate (time) = " + fpRate + "% (" + ttlFPTime + "/" + parser.END_TIME + ")");
    else LOG.log(Level.INFO, "FP rate (time) = " + fpRate + "% ");
    LOG.log(Level.INFO, "");
    LOG.log(Level.INFO, "falseNegatives: " + falseNegatives);

    System.out.println("Average failure detection time: " + avgTime);
    System.out.println("Average first failure detection time: " + avgFirstTime);
    System.out.println("Average last failure detection time: " + avgLastTime);

    System.out.println("Total end-to-end message number: " + ttlMsgNum);
    System.out.println("  Total end-to-end PING message number: " + typeWiseMsgNum[0]);
    System.out.println("      " + typeWiseMsgNum[10] + " direct " + typeWiseMsgNum[11] + " indirect ");
    System.out.println("  Total end-to-end JOIN message number: " + typeWiseMsgNum[1]);
    System.out.println("  Total end-to-end JOIN_ACK message number: " + typeWiseMsgNum[2]);
    System.out.println("  Total end-to-end ACK message number: " + typeWiseMsgNum[3]);
    System.out.println("  Total end-to-end IND_PING message number: " + typeWiseMsgNum[4]);
    System.out.println("  Total end-to-end IND_PING_ACK message number: " + typeWiseMsgNum[5]);
    System.out.println("  Total end-to-end TERMINATE message number: " + typeWiseMsgNum[6]);
    System.out.println("  Total end-to-end CRASH message number: " + typeWiseMsgNum[7]);
    System.out.println("  Total end-to-end UNLUCKY message number: " + typeWiseMsgNum[8]);
    System.out.println("  Total end-to-end REPORT_ALIVE message number: " + typeWiseMsgNum[9]);
    System.out.println("Total node-to-node message number: " + ttlIndMsgNum);

    System.out.println("falsePositives: " + falsePositives);
    System.out.println("FP rate (time) = " + fpRate + "% (" + ttlFPTime + "/" + parser.END_TIME + ")");
    System.out.println("falseNegatives: " + falseNegatives);

    // Print passive feedback summary
    if (passiveUnlucky.size() > 0) {
      printPassiveUnlucky();
    }

    // Print active feedback summary
    if (activeUnlucky.size() > 0) {
      printActiveUnlucky();
    }

    // Print failure detection time details
    StringBuilder str = new StringBuilder();
    if (verbose >= 1){
      str.append("failDetectionTime = [");
      for (Id id : failDetectionTime.keySet()){
        List<Pair<Id, Long>> record = failDetectionTime.get(id);
        List<Long> timeRecord = new ArrayList<>();
        for (Pair<Id, Long> temp : record) {
          timeRecord.add(temp.snd);
        }
        Collections.sort(timeRecord);
        str.append("[");
        for (Long time : timeRecord){
          str.append(time).append(", ");
        }
        str.append("], ");
      }
      str.append("]");
      LOG.log(Level.INFO, str.toString());

      // Print communication cost details
      LOG.log(Level.INFO, "Number of end-to-end messages from each node: ");
      for (Map.Entry<Id, Integer> entry : msgNum.entrySet()){
        LOG.log(Level.INFO, "Node " + entry.getKey().getPort() + " (" + entry.getKey().getTs() + "): " + entry.getValue());
        if (verbose >= 2){
          for (Map.Entry<Id, Integer> entry2 : endEndMsgNum.get((Id)entry.getKey()).entrySet()){
            LOG.log(Level.INFO, "  to Node " + entry2.getKey().getPort() + " (" + entry2.getKey().getTs() + "): " + entry2.getValue());
          }
        }
      }

      LOG.log(Level.INFO, "Number of node-to-node messages from each node: ");
      for (Map.Entry<Id, Integer> entry : indMsgNum.entrySet()){
        LOG.log(Level.INFO, "Node " + entry.getKey().getPort() + " (" + entry.getKey().getTs() + "): " + entry.getValue());
        if (verbose >= 2){
          for (Map.Entry<Id, Integer> entry2 : pairWiseMsgNum.get((Id)entry.getKey()).entrySet()){
            LOG.log(Level.INFO, "  to Node " + entry2.getKey().getPort() + " (" + entry2.getKey().getTs() + "): " + entry2.getValue());
          }
        }
      }
    }

    if (verbose >= 2){
      LOG.log(Level.INFO, "falsePositive record in detail: ");
      for (long[] record : falsePositiveRecord){
        LOG.log(Level.INFO, "  Target: Port " + record[0] + ", Recorder: Port " + record[1] + ", time: " + record[2]);
      }
    }
  }

  private void printActiveUnlucky() {
    System.out.println("\n========== Active Feedback ==========" );
    List<Id> targets=  new ArrayList<>(activeUnlucky.keySet());
    Collections.sort(targets);
    for (Id target: targets) {
      System.out.printf("Node %d checks self unlucky %d times at: [",
          target.getPort(),
          activeUnlucky.get(target).size());

      for (Long time: activeUnlucky.get(target)) {
        System.out.printf("%d, ", time);
      }
      System.out.println("]");
    }
  }

  private void printPassiveUnlucky() {
    System.out.println("\n========= Passive Feedback ==========");
    for (Id observer: passiveUnlucky.keySet()) {
      System.out.printf("From node %d (%d targets): \n",
          observer.getPort(),
          passiveUnlucky.get(observer).size());
      for (Id target: passiveUnlucky.get(observer).keySet()) {
        int num = passiveUnlucky.get(observer).get(target);
        System.out.print("  node " + target.getPort() + ": " + num + ",");
        totalPassive += num;
      }
      System.out.println("");
    }
    System.out.println("----------");
    List<Id> targets = new ArrayList<>(passiveTarget.keySet());
    Collections.sort(targets);
    for (Id target: targets) {
      int target_total = 0;
      for (int value: passiveTarget.get(target).values()) {
        target_total += value;
      }
      System.out.println("  Node " + target.getPort() + " detected unlucky " +target_total);
    }
    System.out.println("----------");
    System.out.println("Total Passive number = " + totalPassive);
    System.out.println("==========");
  }

  @SuppressWarnings("unchecked")
  public void toFile(int verbose){
    if (verbose == 0) return;
    try {
      JSONArray runs = new JSONArray();
      JSONObject obj = new JSONObject();
      JSONObject result = new JSONObject();
      if (this.runNum == 1){
        // cfile = new FileWriter(parser.stats_path, false);
        JSONObject config = new JSONObject();
        String strategy;
        switch(parser.strategy){
          case ACTIVE_FEEDBACK: strategy = "active_feedback";
            break;
          case ACTIVE_FEEDBACK_BAG: strategy = "active_feedback_bag";
            break;
          case ACT_PAS_FEEDBACK: strategy = "act_pas_feedback";
            break;
          case ACT_PAS_FEEDBACK_BAG: strategy = "act_pas_feedback_bag";
            break;
          case NAIVE: strategy = "naive";
            break;
          case NAIVE_BAG: strategy = "naive_bag";
            break;
          case PASSIVE_FEEDBACK: strategy = "passive_feedback";
            break;
          case PASSIVE_FEEDBACK_BAG: strategy = "passive_feedback_bag";
            break;
          default:
            strategy = "";
        }
        config.put("strategy", strategy);
        config.put("topology_path", parser.topology_path);
        config.put("coordinate_path", parser.coordinate_path);
        config.put("powerk", Double.toString(parser.POWERK));
        config.put("msg_drop_rate", Double.toString(parser.MSG_DROP_RATE));
        config.put("radius", Double.toString(parser.RADIUS));
        config.put("delay", Integer.toString(parser.DELAY));
        config.put("delay_one_hop", Integer.toString(parser.DELAY_ONE_HOP));
        config.put("num_run", Integer.toString(parser.NUM_RUN));
        config.put("num_server", Integer.toString(parser.NUM_SERVER));
        config.put("end_time", Long.toString(parser.END_TIME));
        config.put("base_time", Integer.toString(parser.BASE_TIME));
        config.put("ping_rtt", Integer.toString(parser.PING_RTT));
        config.put("round_period_ms", Integer.toString(parser.ROUND_PERIOD_MS));
        config.put("num_ind_contacts", Integer.toString(parser.NUM_IND_CONTACTS));
        config.put("suspect_timeout_ms", Integer.toString(parser.SUSPECT_TIMEOUT_MS));
        config.put("passive_feedback_timeout", Integer.toString(parser.PASSIVE_FEEDBACK_TIMEOUT));
        config.put("unlucky_threshold",Double.toString(parser.UNLUCKY_THRESHOLD));
        config.put("unlucky_alpha", Double.toString(parser.UNLUCKY_ALPHA));
        obj.put("configuration", config);
        obj.put("runs", new JSONArray());
      } else {
        FileReader reader = new FileReader(parser.stats_path);
        JSONParser jsonParser = new JSONParser();
        obj = (JSONObject) jsonParser.parse(reader);
      }

      // FileWriter cfile = new FileWriter(parser.stats_path, false);

      result.put("runNum", Integer.toString(runNum));
      result.put("ttlMsgNum", Integer.toString(ttlMsgNum));
      result.put("ttlIndMsgNum", Integer.toString(ttlIndMsgNum));
  
      long ttlFirstTime = 0;
      long ttlLastTime = 0;
      long ttlTime = 0;
      int reportCount = 0;
      for (Id id : failDetectionTime.keySet()){
        List<Pair<Id, Long>> record = failDetectionTime.get(id);
        List<Long> timeRecord = new ArrayList<>();
        for (Pair<Id, Long> temp : record) {
          timeRecord.add(temp.snd);
        }
        Collections.sort(timeRecord);
        for (int i =0; i < timeRecord.size(); i++){
          if (i == 0)
            ttlFirstTime += timeRecord.get(i);
          if (i == timeRecord.size()-1)
            ttlLastTime += timeRecord.get(i);
          ttlTime += timeRecord.get(i);
          reportCount++;
        }
      }
      falseNegatives = 0;
      for (List<Pair<Id, Long>> record : failDetectionTime.values()) {
        falseNegatives += parser.NUM_SERVER - failDetectionTime.size() - record.size();
      }
      result.put("falseNegatives", Integer.toString(falseNegatives));
      int avgFirstTime = 0;
      int avgLastTime = 0;
      int avgTime = 0;
      if (failDetectionTime.size() != 0){
        avgFirstTime = (int)ttlFirstTime / failDetectionTime.size();
        avgLastTime = (int)ttlLastTime / failDetectionTime.size();
      }
      if (reportCount != 0)
        avgTime = (int)ttlTime / reportCount;
      result.put("avgTime", Integer.toString(avgTime));
      result.put("avgFirstTime", Integer.toString(avgFirstTime));
      result.put("avgLastTime", Integer.toString(avgLastTime));

      // System.out.println("detectionError: " + detectionError);
      result.put("falsePositives", Integer.toString(falsePositives));
      double fpRate = (double) ttlFPTime / (double) parser.END_TIME * 100.0;
      result.put("fpRate", Double.toString(fpRate));
  
      if (verbose >= 1) {
        JSONObject detectTimes = new JSONObject();
        for (Id id : failDetectionTime.keySet()){
          List<Pair<Id, Long>> record = failDetectionTime.get(id);
          JSONArray detectTime = new JSONArray();
          for (Pair<Id, Long> detail : record) {
            JSONObject failDetail = new JSONObject();
            failDetail.put("recorder", Integer.toString(detail.fst.getPort()));
            failDetail.put("time", Long.toString(detail.snd));
            detectTime.add(failDetail);
          }
          detectTimes.put(Integer.toString(id.getPort()), detectTime);
        }
        result.put("failDetectionTime", detectTimes);

        JSONObject endEndMsg = new JSONObject();
        // node key: "{node_port}|{node_join_time}"
        for (Map.Entry<Id, Integer> entry : msgNum.entrySet()){
          String key = entry.getKey().getPort().toString() + "|" + entry.getKey().getTs().toString();
          JSONObject currNode = new JSONObject();
          currNode.put("total", entry.getValue().toString());
          if (verbose >= 2){
            for (Map.Entry<Id, Integer> entry2 : endEndMsgNum.get(entry.getKey()).entrySet()){
              String key2 = entry2.getKey().getPort().toString() + "|" + entry2.getKey().getTs().toString();
              currNode.put(key2, entry2.getValue().toString());
            }
          }
          endEndMsg.put(key, currNode);
        }
        result.put("endEndMsg", endEndMsg);

        JSONObject hopHopMsg = new JSONObject();
        for (Map.Entry<Id, Integer> entry : indMsgNum.entrySet()){
          String key = entry.getKey().getPort().toString() + "|" + entry.getKey().getTs().toString();
          JSONObject currNode = new JSONObject();
          currNode.put("total", entry.getValue().toString());
          if (verbose >= 2){
            for (Map.Entry<Id, Integer> entry2 : pairWiseMsgNum.get((Id)entry.getKey()).entrySet()){
              String key2 = entry2.getKey().getPort().toString() + "|" + entry2.getKey().getTs().toString();
              currNode.put(key2, entry2.getValue().toString());
            }
          }
          hopHopMsg.put(key, currNode);
        }
        result.put("hopHopMsg", hopHopMsg);
        totalPassive = 0;
        for (Id observer: passiveUnlucky.keySet()) {
          for (Id target: passiveUnlucky.get(observer).keySet()) {
            totalPassive += passiveUnlucky.get(observer).get(target);
          }
        }
        result.put("ttlPassive", Integer.toString(totalPassive));
        result.put("ttlPingToFailedNode", Integer.toString(ttlPingToFailedNode));
        JSONArray detectDist = new JSONArray();
        for (double dist : firstDetectDist) {
          detectDist.add(Double.toString(dist));
        }
        result.put("firstDetectDistance", detectDist);

        JSONObject msgType = new JSONObject();
        msgType.put("PING", Integer.toString(typeWiseMsgNum[0]));
        msgType.put("CAUSED_PING", Integer.toString(typeWiseMsgNum[10]));
        msgType.put("CAUSED_IND_PING", Integer.toString(typeWiseMsgNum[11]));
        msgType.put("JOIN", Integer.toString(typeWiseMsgNum[1]));
        msgType.put("JOIN_ACK", Integer.toString(typeWiseMsgNum[2]));
        msgType.put("ACK", Integer.toString(typeWiseMsgNum[3]));
        msgType.put("IND_PING", Integer.toString(typeWiseMsgNum[4]));
        msgType.put("IND_PING_ACK", Integer.toString(typeWiseMsgNum[5]));
        msgType.put("TERMINATE", Integer.toString(typeWiseMsgNum[6]));
        msgType.put("CRASH", Integer.toString(typeWiseMsgNum[7]));
        msgType.put("UNLUCKY", Integer.toString(typeWiseMsgNum[8]));
        msgType.put("REPORT_ALIVE", Integer.toString(typeWiseMsgNum[9]));
        result.put("msgType", msgType);
      }

      if (verbose >= 2){
        JSONArray falsePositiveDetail = new JSONArray();
        for (long[] record : falsePositiveRecord){
          JSONObject detail = new JSONObject();
          detail.put("target", Long.toString(record[0]));
          detail.put("recorder", Long.toString(record[1]));
          detail.put("time", Long.toString(record[2]));
          falsePositiveDetail.add(detail);
        }
        result.put("falsePositiveDetail", falsePositiveDetail);
      }

      runs = (JSONArray) obj.get("runs");
      runs.add(result);
      obj.put("runs", runs);

      // try to save a nicely looking json
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      JsonElement je = JsonParser.parseString(obj.toJSONString());
      String prettyJson = gson.toJson(je);
      // cfile.write(prettyJson);
      // cfile.flush();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
