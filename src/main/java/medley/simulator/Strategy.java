package medley.simulator;

public enum Strategy {
  NAIVE,
  NAIVE_BAG,
  PASSIVE_FEEDBACK,      // pinging node monitor unlucky case only.
  PASSIVE_FEEDBACK_BAG,
  ACTIVE_FEEDBACK,       // unlucky node actively report only.
  ACTIVE_FEEDBACK_BAG,
  ACT_PAS_FEEDBACK,
  ACT_PAS_FEEDBACK_BAG,  // unlucky node report + pinging node monitor
}
