Overview
-------------------
This is an extension of Medley [[1]](#1) failure detector with feedback-based
optimizations to reduce tail failure detection time caused by unlucky nodes.

Prerequisites
--------------------
- Java 1.8
- Apache Maven 3.2+

How to build
----------

This project is built using Apache maven. Run the following command in your
terminal.
```
$ mvn clean package
```

How to run
----------
There are a few scripts in `bin/` folder to start simulations under certain
configurations. To start an example run:
```
$ cd bin
$ ./run.sh
```
To perform multiple runs under different configurations or to compare different
strategies, we provide a configuration file for experiment set up with some 
example scripts in `bin/` folder for integrated runs.  
We support configurations on (example in `bin/config.json`):
- `strategy`: `naive_bag`, `active_feedback_bag`, `passive_feedback_bag`, 
`act_pas_feedback_bag`, which runs Medley in bag strategy [[1]](#1) with no
optimization, active-feedback only, passive-feedback only, and both optimizations
respectively. We also support `naive` which runs Medley with pure probability based
target selection without bag strategy applied.
- `generate_new_topology`: generate new topology (for random/cluster topology type)
if set as `true`.
- `topology_type`: support random, cluster, or grid.
- `coordinate_path`: file path for topology coordinate information.
- `stats_path`: file path to store stats after expr run.
- `to_file`: record stats to file if set as `true`.
- `powerk`: set exponential value. Pings tend to stay more local if set as higher
value. Suggested value is 3 to balance the failure detection time and communication
costs.
- `msg_drop_rate`: set to simulate message drop rate.
- `one_hop_radius`: used to set the communication range of each node.
- `delay`: set for simulated network layer maximal message travel time for one hop.
- `length`: length of the topology area (square).
- `delay_one_hop`: deprecated.
- `num_run`: number of runs. Statistics will be recorded separately for each run but
in the same file.
- `num_server`: number of node in the topology.
- `end_time`: finish time of a run.
- `base_time`: base time to set period time
- `ping_rtt`: rtt time Medley used to decide whether PINGs are acknowledged or not.
If not set, default as `base_time`.
- `round_period_ms`: time length of a time period. Set as 4X of `base_time` if not
set.
- `num_ind_contacts`: number of indirect pings if the initial pings are not
acknowledged.
- `suspect_timeout_ms`: suspicious timeout.
- `passive_feedback_timeout`: passive feedback timeout. If a node `N` does not get
any update from a member `M` longer than this timeout, it has a chance to apply 
passive feedback mechanism to ping `M` in the next period.
- `unlucky_threshold`: threshold for active-feedback mechanism. If a node finds that
its estimated pinged interval is longer than this threshold, it considers itself as 
unlucky and reports to its direct neighbors.
- `unlucky_alpha`: parameter for exponential average mechanism used to get estimated
pinged interval in active-feedback mechanism.
- `verbose`: level of logging details.
- `events`: list of failure/rejoin events in the network. Each element includes
information about the target node `server`, fail/rejoin `time`, and `mode` which
shows whether it's a failure or rejoin event.

We provided a few examples to run multiple runs under different settings/strategies in:
- `random.sh`: runs experiments with multiple runs in random topology. One 
failure per run.  
- `simul_random.sh`: runs experiments with simultaneous failures under different settings.
- `domain_random.sh`: runs domain failure experiments under cluster topology.

Brief Information in other folders
------
- `analyze/` folder includes the data for extended version of the paper and scripts
to generate plots in the paper.
  - Each subfolder includes stats, configurations, and topologies for different
configurations or runs.
- `topology/` folder includes some example topology coordinates and their visualizations.

## Reference
<a id="1">[1]</a>
Yang, R., Zhu, S., Li, Y., & Gupta, I. (2019, December). Medley: A novel 
distributed failure detector for IoT networks. In Proceedings of the 20th
International Middleware Conference (pp. 319-331).

