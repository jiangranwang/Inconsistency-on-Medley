package medley.service;


import com.google.common.cache.Cache;
import com.sun.tools.javac.util.Pair;
import medley.simulator.*;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.logging.Level;

import static medley.simulator.Utils.transformMapToList;

public class EventService {
  public Id id;
  public final Server server;
  private static final Logger LOG = Logger.getLogger(Server.class.getName());
  private final EventServiceFactory networkService;

  private final ConcurrentHashMap<Id, Pair<Integer, Status>> membershipTable;
  private final Cache<Id, Pair<Integer, Status>> changeTable;
  private final ConcurrentHashMap<Id, Long> suspectTable;
  private final ConcurrentHashMap<Id, Integer> suspectCounts;
  private final BlockingQueue<Id> receivedIds;
  private static volatile boolean stopFlag = false;
  private boolean disabled = false;
  private static final AtomicInteger countForStop = new AtomicInteger(3);

  // For simulator
  public PriorityQueue<Event> pendingEvents;
  public long nextEventTs;

  public EventService(final Server server,
                      final ConcurrentHashMap<Id, Pair<Integer, Status>> membershipTable,
                      final Cache<Id, Pair<Integer, Status>> changeTable,
                      final ConcurrentHashMap<Id, Long> suspectTable,
                      final ConcurrentHashMap<Id, Integer> suspectCounts,
                      final BlockingQueue<Id> receivedIds) {
    this.id = server.id;
    this.server = server;
    this.networkService = EventServiceFactory.getInstance();
    this.networkService.registerEventService(this);
    this.membershipTable = membershipTable;
    this.changeTable = changeTable;
    this.suspectTable = suspectTable;
    this.suspectCounts = suspectCounts;
    this.receivedIds = receivedIds;
    this.pendingEvents = new PriorityQueue<>();
  }

  public void setID(Id id){
    this.networkService.removeEventService(this.id);
    this.id = id;
    this.networkService.registerEventService(this);
    this.pendingEvents = new PriorityQueue<>(); //  clear all pending events
  }

  public void shutdown() {
    disabled = true;
  }

  public void restart(long current_time) {
    disabled = false;
  }

  public void join(long current_time){
    disabled = false;
    final Message join = Message.newBuilder()
                                .setSenderId(id)
                                .setInitiatorId(id)
                                .setCreatorId(id)
                                .setType(Type.JOIN)
                                .setTable(transformMapToList(changeTable.asMap()))
                                .setSenderIncarnation(membershipTable.get(id).fst)
                                .build();
    Id target_id = null;
    for (Map.Entry<Id, Pair<Integer, Status>> entry : membershipTable.entrySet()){
      if (entry.getValue().snd == Status.ACTIVE){
        target_id = (Id)entry.getKey();
        if (!Objects.equals(target_id, id))
          break; // found an ACTIVE node that is not myself
      }
    }
    for (Id id : server.getNeighbors()){ // prioritize to send to neighbors
      if (id != null)
        target_id = id;
    }
    if (target_id == null){
      LOG.log(Level.SEVERE, "ABORT JOIN: No nodes are recorded ACTIVE");
      return;
    }
    sendMessage(target_id, join, current_time);
  }

  public void addEvent(Event event) {
    if (disabled)
      return;
    LOG.log(Level.FINER, "    Node {0} get an event {1} for node {2} time {3}",
        new Object[]{id.getPort(), event.eventType, event.eventTarget, event.eventTime});
    this.pendingEvents.add(event);
    nextEventTs = this.pendingEvents.peek() != null ? this.pendingEvents.peek().eventTime : Long.MAX_VALUE;
    server.updateNextEventTime(nextEventTs);
  }

  public void executeNextEvent() throws Exception {
    if (disabled)
      return;
    Event event = this.pendingEvents.poll();
    if (event == null) {
      LOG.log(Level.WARNING, "Error processing event.");
      return;
    }
    LOG.log(Level.FINER, "{0}: Node {1} is handling event {2} for target {2}",
        new Object[]{event.eventTime, id.getPort(), event.eventType, event.eventTarget});
    if (event.message.getCreatorId() != null &&
        !event.message.getCreatorId().equals(this.id) && event.eventTarget.equals(this.id)){
      server.statsRecorder.ttlMsgNum++;
      server.statsRecorder.incrementMsgNum(
          event.message.getCreatorId(),
          event.message.getType(),
          event.message.getInitiatorId(),
          event.eventTarget);
      server.statsRecorder.incrementEndEndMsgNum(event.message.getCreatorId(), id);
    }
    if (event.eventType == EventType.REGULAR_PING) {
      if (event.eventTarget.equals(this.id)) { // The message is for me
        LOG.log(Level.FINEST, "     Node {0} is handling {1}.", new Object[]{id.getPort(), event.eventType});
        messageHandler(event.message, event.eventTime);
      } else { // I'm the relay
        LOG.log(Level.FINEST, "     Node {0} is relaying {1}.", new Object[]{id.getPort(), event.eventType});
        sendMessage(event.eventTarget, event.message, event.eventTime);
      }
    } else if (event.eventType == EventType.ACK_CHECK) {
      server.handleACKCHECK(event);
    } else if (event.eventType == EventType.IND_ACK_CHECK) {
      server.handleINDACKCHECK(event);
    } else if (event.eventType == EventType.FAIL_CHECK) {
      server.handleFAILCHECK(event);
    } else {
      // TODO
      LOG.log(Level.FINE, "Received XX type of msg");
    }
    nextEventTs = this.pendingEvents.peek() != null ? this.pendingEvents.peek().eventTime : Long.MAX_VALUE;
  }

  public void executeEventBeforeTs(long time) throws Exception {
    while (!this.pendingEvents.isEmpty() && this.pendingEvents.peek().eventTime < time) {
      this.executeNextEvent();
    }
    nextEventTs = this.pendingEvents.peek() != null ? this.pendingEvents.peek().eventTime : Long.MAX_VALUE;
  }

  public void sendMessage(Id target_id, Message msg, long current_time) {
    int next_hop_index = this.server.routingTable.getOrDefault(target_id.getPort(), target_id.getPort());
    if (target_id.getPort() != next_hop_index) {
      LOG.log(Level.FINE, "{0}: Node {1} relay message type {2} to node {3} through {4}",
              new Object[]{current_time, id.getPort(), msg.getType(), target_id.getPort(), next_hop_index});
    } else {
      LOG.log(Level.FINE, "{0}: Node {1} send message type {2} to node {3} through {4}",
              new Object[]{current_time, id.getPort(), msg.getType(), target_id.getPort(), next_hop_index});
    }
    msg.setSenderId(id);
    this.networkService.send(target_id.getPort(), next_hop_index, msg, current_time);
  }

  // From original InboundChannelHandler
  private void mergeActive(final Id targetId, int incarnation, long current_time) {
    final Pair<Integer, Status> prev = membershipTable.get(targetId);
    if (prev != null && prev.snd == Status.SUSPECTED && prev.fst < incarnation) {
      LOG.log(Level.INFO, "{0}: {1} merge {2} from SUSPECTED to ACTIVE.",
          new Object[]{current_time, id.getPort(), targetId});
      membershipTable.put(targetId, new Pair<>(incarnation, Status.ACTIVE));
      changeTable.put(targetId, new Pair<>(incarnation, Status.ACTIVE));
      suspectTable.remove(targetId);

    } else if (prev != null && prev.snd == Status.ACTIVE && prev.fst < incarnation) {
      membershipTable.put(targetId, new Pair<>(incarnation, Status.ACTIVE));
      changeTable.put(targetId, new Pair<>(incarnation, Status.ACTIVE));

    } else if (prev == null || prev.snd == Status.FAILED) {
      if (!changeTable.asMap().containsKey(targetId) || changeTable.asMap().get(targetId).fst < incarnation) {
        // add false positive node back to membership list
        membershipTable.put(targetId, new Pair<>(incarnation, Status.ACTIVE));
        changeTable.put(targetId, new Pair<>(incarnation, Status.ACTIVE));
        server.routingTable = networkService.getRoutingTable(server.id);
        server.neighbour_distance = networkService.getNeighbourDistance(server.id);
        LOG.log(Level.INFO, "{0}: {1} merge {2} from FAILED to ACTIVE.",
                new Object[]{current_time, id.getPort(), targetId});
      }
      server.statsRecorder.resolveFP(targetId, id, current_time);
    }
  }

  private void mergeSuspected(final Id targetId, int incarnation, long current_time) {
    final Pair<Integer, Status> prev = membershipTable.get(targetId);
    if (prev != null && prev.snd == Status.ACTIVE && prev.fst <= incarnation) {
      if (targetId.equals(id)) {
        LOG.log(Level.INFO, "{0}: {1} realized self-suspected. Reports Alive.",
            new Object[]{current_time, id.getPort(), targetId});
        changeTable.put(targetId, new Pair<>(incarnation + 1, Status.ACTIVE));
        membershipTable.put(targetId, new Pair<>(incarnation + 1, Status.ACTIVE));
        // actively notify neighbors
        final Message report_alive = Message.newBuilder()
                .setSenderId(id)
                .setInitiatorId(id)
                .setCreatorId(id)
                .setType(Type.REPORT_ALIVE)
                .setTable(transformMapToList(changeTable.asMap()))
                .setSenderIncarnation(incarnation + 1)
                .build();
        List<Id> neighbors = server.getNeighbors();
        for (Id neighbor : neighbors)
          sendMessage(neighbor, report_alive, current_time);
      } else {
        LOG.log(Level.INFO, "{0}: {1} merge {2} from ACTIVE to SUSPECTED.",
            new Object[]{current_time, id.getPort(), targetId});
        membershipTable.put(targetId, new Pair<>(incarnation, Status.SUSPECTED));
        changeTable.put(targetId, new Pair<>(incarnation, Status.SUSPECTED));
        suspectTable.putIfAbsent(targetId, current_time);
        suspectCounts.put(targetId, suspectCounts.getOrDefault(targetId, 0) + 1);
      }

    } else if (!targetId.equals(id) && prev != null && prev.snd == Status.SUSPECTED && prev.fst < incarnation) {
      suspectTable.put(targetId, current_time);
      membershipTable.put(targetId, new Pair<>(incarnation, Status.SUSPECTED));
      changeTable.put(targetId, new Pair<>(incarnation, Status.SUSPECTED));
      suspectCounts.put(targetId, suspectCounts.getOrDefault(targetId, 0) + 1);
    } else if (prev != null && prev.snd != Status.SUSPECTED) {
      LOG.log(Level.INFO, "{0}: Unexpected status transition for {1} from {2} to SUSPECTED",
              new Object[]{current_time, targetId, prev.snd});
    }
  }

  private void mergeFailed(final Id targetId, int incarnation, final Long current_time) {
    if (targetId.equals(id)) {
      // false positive
      LOG.log(Level.INFO, "Merge false positive for {0}", targetId);
      changeTable.put(targetId, new Pair<>(incarnation + 1, Status.ACTIVE));
      membershipTable.put(targetId, new Pair<>(incarnation + 1, Status.ACTIVE));
      // actively notify neighbors
      final Message report_alive = Message.newBuilder()
              .setSenderId(id)
              .setInitiatorId(id)
              .setCreatorId(id)
              .setType(Type.REPORT_ALIVE)
              .setTable(transformMapToList(changeTable.asMap()))
              .setSenderIncarnation(incarnation + 1)
              .build();
      List<Id> neighbors = server.getNeighbors();
      for (Id neighbor : neighbors)
        sendMessage(neighbor, report_alive, current_time);
      return;
    }

    final Pair<Integer, Status> prev = membershipTable.get(targetId);
    if (prev != null) {
      if (prev.fst > incarnation) {
        LOG.log(Level.FINER, "{0}: Node {1} gets out of date FAILURE for {2}",
            new Object[]{current_time, this.id.getPort(), targetId.getPort()}
        );
      } else {
        if (membershipTable.remove(targetId) != null) server.statsRecorder.recordFail(targetId, id, current_time, -1.0);
        suspectTable.remove(targetId);
        changeTable.put(targetId, new Pair<>(incarnation, Status.FAILED));
        server.routingTable = networkService.getRoutingTable(server.id);
        server.neighbour_distance = networkService.getNeighbourDistance(server.id);
        LOG.log(Level.INFO, "{0}: Node {1} merge {2} as FAILED.", new Object[]{current_time, this.id.getPort(), targetId});
      }
    }
  }

  private void mergeJoin(final Id targetId, int incarnation) {
    if (membershipTable.putIfAbsent(targetId, new Pair<>(incarnation, Status.ACTIVE)) == null) {
      LOG.log(Level.INFO, "Sever {0}: {1} joined. Set it ACTIVE", new Object[]{id, targetId});
      changeTable.put(targetId, new Pair<>(incarnation, Status.JOIN));
      server.routingTable = networkService.getRoutingTable(server.id);
      server.neighbour_distance = networkService.getNeighbourDistance(server.id);
      server.updateRunningProbList(server.setInitialProbabilityList(server.updateInitialProbabilityList())); // new node joins, update probability list
    } else {
      changeTable.put(targetId, new Pair<>(incarnation, Status.ACTIVE));
    }
  }

  public void messageHandler(final Message msg, long current_time) throws Exception {
    if (msg.getType() == Type.TERMINATE) {
      LOG.log(Level.INFO, "Receives a TERMINATE message. Marks itself as failed and waits until it disseminates info");
      membershipTable.remove(id);
      changeTable.put(id, new Pair<>(-1, Status.FAILED));
      stopFlag = true;
      return;

    } else if (msg.getType() == Type.CRASH) {
      LOG.log(Level.INFO, "Receives a CRASH message");
      // Just crash
      System.exit(1);
    }

    LOG.log(Level.FINE, "{0}: Node {1} receive a message type {2} from node {3}",
            new Object[]{current_time, id.getPort(), msg.getType(), msg.getCreatorId().getPort()});
    server.updateActive(msg.getSenderId(), current_time);
    server.updateActive(msg.getCreatorId(), current_time);
    if (msg.getType() == Type.PING) {
      // send reply
      final Message response = Message.newBuilder()
          .setSenderId(id)
          .setInitiatorId(msg.getInitiatorId())
          .setCreatorId(id)
          .setType(Type.ACK)
          .setTable(transformMapToList(changeTable.asMap()))
          .setSenderIncarnation(membershipTable.get(id).fst)
          .build();
      sendMessage(msg.getCreatorId(), response, current_time);
      server.checkSelfUnlucky(current_time, true);

    } else if (msg.getType() == Type.UNLUCKY) {
      server.unluckyNeighborHandler(msg.getInitiatorId());

    } else if (msg.getType() == Type.JOIN) {
      LOG.log(Level.INFO, "Server {0} joins the network. Updates its status to ACTIVE", msg.getCreatorId());
      // add to membershipTable
      membershipTable.put(msg.getCreatorId(), new Pair<>(msg.getSenderIncarnation(), Status.ACTIVE));
      LOG.log(Level.INFO, "Add JOIN into the change table for {0}", msg.getCreatorId());
      changeTable.put(msg.getCreatorId(), new Pair<>(msg.getSenderIncarnation(), Status.JOIN));
      server.updateRunningProbList(server.setInitialProbabilityList(server.updateInitialProbabilityList())); // new node joins, update probability list
      // Send back membershipTable
      final Message response = Message.newBuilder()
          .setSenderId(id)
          .setCreatorId(id)
          .setType(Type.JOIN_ACK)
          .setTable(transformMapToList(membershipTable))
          .setSenderIncarnation(membershipTable.get(id).fst)
          .build();
      sendMessage(msg.getCreatorId(), response, current_time);

    } else if (msg.getType() == Type.JOIN_ACK) {
      // Put all the elements in the received membership table into me.
      for (final Entry entry : msg.getTable()) {
        if (!membershipTable.put(entry.getId(), new Pair<>(-1, entry.getStatus())).equals(new Pair<>(-1, entry.getStatus()))) {
          LOG.log(Level.INFO, "Update Server {0} status to {1}", new Object[]{entry.getId(), entry.getStatus()});
        }
      }
      receivedIds.offer(msg.getCreatorId());

    } else if (msg.getType() == Type.REPORT_ALIVE) {
      mergeActive(msg.getCreatorId(), msg.getSenderIncarnation(), current_time);

    } else if (msg.getType() == Type.ACK) {
      // merge table
      mergeTable(msg, current_time);

      // if this is ack for indirect ping, respond to ping initiator
      if (!msg.getInitiatorId().equals(id)) {
        final Message ind_ack = Message.newBuilder()
            .setSenderId(id)
            .setTargetId(msg.getCreatorId())
            .setCreatorId(msg.getCreatorId())
            .setType(Type.IND_PING_ACK)
            .setTable(transformMapToList(changeTable.asMap()))
            .setSenderIncarnation(membershipTable.get(id).fst)
            .build();
        sendMessage(msg.getInitiatorId(), ind_ack, current_time);

      } else {
        receivedIds.offer(msg.getCreatorId());
      }

      if (stopFlag && countForStop.decrementAndGet() == 0) {
        LOG.log(Level.INFO, "Dissemination for termination finished. Stopping a server");
        Server.stopped = true;
      }
    } else if (msg.getType() == Type.IND_PING) {
      final Message ping = Message.newBuilder()
          .setSenderId(id)
          .setInitiatorId(msg.getInitiatorId())
          .setCreatorId(id)
          .setType(Type.PING)
          .setTable(transformMapToList(changeTable.asMap()))
          .setSenderIncarnation(membershipTable.get(id).fst)
          .build();
      // LOG.log(Level.INFO, "{0}: {1} send ping to {2}", new Object[]{current_time, id.getPort(), msg.getTargetId().getPort()});
      sendMessage(msg.getTargetId(), ping, current_time);

    } else if (msg.getType() == Type.IND_PING_ACK) {
      // merge table
      mergeTable(msg, current_time);
      // mergeActive(msg.getTargetId(), current_time);
      receivedIds.offer(msg.getTargetId());
    }
  }

  private void mergeTable(Message msg, long current_time) {
    for (final Entry recEntry : msg.getTable()) {
      final Id targetId = recEntry.getId();
      final Status targetNewStatus = recEntry.getStatus();
      int incarnation = recEntry.getIncarnation();
      //System.out.println(current_time + "," + targetId.getPort() + "," + targetNewStatus + "," + incarnation);
      if (targetNewStatus == Status.ACTIVE) { // Active
        mergeActive(targetId, incarnation, current_time);
      } else if (targetNewStatus == Status.SUSPECTED) { //Suspected
        mergeSuspected(targetId, incarnation, current_time);
      } else if (targetNewStatus == Status.FAILED) { //Failed
        mergeFailed(targetId, incarnation, current_time);
      } else if (targetNewStatus == Status.JOIN) {
        mergeJoin(targetId, incarnation);
      }
    }
    // update the status of the sender
    mergeActive(msg.getSenderId(), msg.getSenderIncarnation(), current_time);
  }

  // End of original Inbound Channel Handler.
}
