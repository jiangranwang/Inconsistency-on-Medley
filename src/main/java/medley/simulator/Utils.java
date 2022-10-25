package medley.simulator;

import com.sun.tools.javac.util.Pair;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class
 */
public class Utils {
  private Utils() {
  }

  public static InetSocketAddress getAddressFromId(final Id id) {
    return new InetSocketAddress(id.getHostname().toString(), id.getPort());
  }

  // transform status table to list
  public static List<Entry> transformMapToList(final Map<Id, Pair<Integer, Status>> table){
    final List<Entry> sentTable= new ArrayList<>();
    for (final Map.Entry<Id, Pair<Integer, Status>> mapEntry: table.entrySet()){
      Entry messageEntry = new Entry(mapEntry.getKey(), mapEntry.getValue().fst, mapEntry.getValue().snd);
      sentTable.add(messageEntry);
    }
    return sentTable;
  }
}

