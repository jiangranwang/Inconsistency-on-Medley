package medley.utils;

import medley.simulator.Strategy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.io.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

class ListComparator<T extends Comparable<T>> implements Comparator<long[]> {
  @Override
  public int compare(long[] o1, long[] o2) {
    if (o1 == o2) return 0;
    return o1[0] > o2[0] ? 1 : -1;
  }
}

public class ConfigParser{
  public Strategy strategy;
  public boolean generate_new_topology;
  public boolean use_churn;
  public String topology_type;
  public String topology_path;
  public String routing_path;
  public String membership_path;
  public String churn_path;
  public boolean optimize_route;
  public String distance_metric;
  public String coordinate_path;
  public String stats_path;
  public boolean to_file;
  public int length;
  public double mobile_percentage;
  public double mobile_distance;

  public double POWERK;
  public double MSG_DROP_RATE;
  public double RADIUS;
  public int DELAY;
  public int DELAY_ONE_HOP;

  public int NUM_RUN;
  public int NUM_SERVER;
  public long END_TIME;
  public int BASE_TIME;
  public int PING_RTT;
  public int ROUND_PERIOD_MS;
  public int NUM_IND_CONTACTS;
  public int CHANGE_TABLE_TIMEOUT_MS;
  public int CHANGE_TABLE_SIZE;
  public int SUSPECT_TIMEOUT_MS;

  public int PASSIVE_FEEDBACK_TIMEOUT;
  public int UNLUCKY_THRESHOLD;
  public double UNLUCKY_ALPHA;
  public double CHURN_RATIO;
  public int VERBOSE = 0;

  public List<long[]> eventList;

  public ConfigParser() {
    eventList = new ArrayList<>();
  }

  private Strategy getStrategy(String str) {
    switch (str) {
      case "naive":
        return Strategy.NAIVE;
      case "naive_bag":
        return Strategy.NAIVE_BAG;
      case "passive_feedback":
        return Strategy.PASSIVE_FEEDBACK;
      case "passive_feedback_bag":
        return Strategy.PASSIVE_FEEDBACK_BAG;
      case "active_feedback":
        return Strategy.ACTIVE_FEEDBACK;
      case "active_feedback_bag":
        return Strategy.ACTIVE_FEEDBACK_BAG;
      case "act_pas_feedback":
        return Strategy.ACT_PAS_FEEDBACK;
      case "act_pas_feedback_bag":
        return Strategy.ACT_PAS_FEEDBACK_BAG;
      default:
        System.out.println("No such strategy.");
        System.exit(0);
    }
    return Strategy.NAIVE;
  }

  public boolean parse(String config_path){
    JSONParser jsonParser = new JSONParser();

    try (FileReader reader = new FileReader(config_path))
    {
      Object obj = jsonParser.parse(reader);
      JSONObject config = (JSONObject) obj;
      strategy = getStrategy((String) config.get("strategy"));
      generate_new_topology = ((String) config.get("generate_new_topology")).equals("true");
      use_churn = ((String) config.get("use_churn")).equals("true");
      topology_type = (String) config.get("topology_type");
      topology_path = (String) config.get("topology_path");
      routing_path = (String) config.get("routing_path");
      optimize_route = (config.get("optimize_route")).equals("true");
      membership_path = (String) config.get("membership_path");
      distance_metric = (String) config.get("distance_metric");
      coordinate_path = (String) config.get("coordinate_path");
      churn_path = (String) config.get("churn_path");
      stats_path = (String) config.get("stats_path");
      to_file = ((String) config.get("to_file")).equals("true");
      length = Integer.parseInt((String) config.get("length"));
      mobile_percentage = Double.parseDouble((String) config.get("mobile_percentage"));
      mobile_distance = Double.parseDouble((String) config.get("mobile_distance"));

      POWERK = Double.parseDouble((String) config.get("powerk"));
      MSG_DROP_RATE = Double.parseDouble((String) config.get("msg_drop_rate"));
      RADIUS = Double.parseDouble((String) config.get("one_hop_radius"));
      DELAY = Integer.parseInt((String) config.get("delay"));
      DELAY_ONE_HOP = Integer.parseInt((String) config.get("delay_one_hop"));

      NUM_RUN = Integer.parseInt((String) config.get("num_run"));
      NUM_SERVER = Integer.parseInt((String) config.get("num_server"));
      END_TIME = Long.parseLong((String) config.get("end_time"));
      BASE_TIME = Integer.parseInt((String) config.get("base_time"));
      PING_RTT = Integer.parseInt((String) config.get("ping_rtt"));
      ROUND_PERIOD_MS = Integer.parseInt((String) config.get("round_period_ms"));
      NUM_IND_CONTACTS = Integer.parseInt((String) config.get("num_ind_contacts"));
      CHANGE_TABLE_TIMEOUT_MS = Integer.parseInt((String) config.get("cache_timeout"));
      CHANGE_TABLE_SIZE = Integer.parseInt((String) config.get("cache_size"));
      SUSPECT_TIMEOUT_MS = Integer.parseInt((String) config.get("suspect_timeout_ms"));

      PASSIVE_FEEDBACK_TIMEOUT = Integer.parseInt((String) config.get("passive_feedback_timeout"));
      UNLUCKY_THRESHOLD = Integer.parseInt((String) config.get("unlucky_threshold"));
      UNLUCKY_ALPHA = Double.parseDouble((String) config.get("unlucky_alpha"));
      CHURN_RATIO = Double.parseDouble((String) config.get("churn_ratio"));

      VERBOSE = Integer.parseInt((String) config.get("verbose"));

      JSONArray events = (JSONArray) config.get("events");
      for (Object entry : events) {
        JSONObject event = (JSONObject) entry;
        long[] params = new long[3];
        params[0] = Long.parseLong((String) event.get("server"));
        params[1] = Long.parseLong((String) event.get("time"));
        String action = (String) event.get("mode");
        switch (action) {
          case "shutdown":
            params[2] = 0L;
            break;
          case "restart":
            params[2] = 1L;
            break;
          case "conclude":
            params[2] = 2L;
            break;
          case "domain":
            params[2] = 3L;
            break;
          case "simul":
            params[2] = 4L;
            break;
          default:
            params[2] = 5L;
            break;
        }
        eventList.add(params);
      }
      eventList.sort(new ListComparator<>());
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  public void copyProperties(final Object to) {
    Map<String, Field> myFields = analyze(this);
    Map<String, Field> toFields = analyze(to);
    myFields.keySet().retainAll(toFields.keySet());
    for (Map.Entry<String, Field> fromFieldEntry : myFields.entrySet()) {
      final String name = fromFieldEntry.getKey();
      final Field sourceField = fromFieldEntry.getValue();
      final Field targetField = toFields.get(name);
      if (targetField.getType().isAssignableFrom(sourceField.getType())) {
        sourceField.setAccessible(true);
        if (Modifier.isFinal(targetField.getModifiers())) continue;
        targetField.setAccessible(true);
        try {
          targetField.set(to, sourceField.get(this));
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

  private static Map<String, Field> analyze(Object object) {
    if (object == null) System.exit(0);

    Map<String, Field> map = new HashMap<>();

    Class<?> obj = object.getClass();
    for (Field field : obj.getDeclaredFields()) {
      if (!map.containsKey(field.getName())) {
        map.put(field.getName(), field);
      }
    }
    return map;
  }
}
