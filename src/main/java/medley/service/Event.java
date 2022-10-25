package medley.service;

import medley.simulator.Id;
import medley.simulator.Message;

public class Event implements Comparable<Event> {
  public EventType eventType;
  public Id eventTarget;
  public long eventTime;
  public Message message;

  public Event(EventType type, Id target_id, final long time, final Message msg) {
    this.eventType = type;
    this.eventTarget = target_id;
    this.eventTime = time;
    this.message = msg;
  }

  @Override
  public int compareTo(Event in) {
    return Long.compare(this.eventTime, in.eventTime);
  }
}
