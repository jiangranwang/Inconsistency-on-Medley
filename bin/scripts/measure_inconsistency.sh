run_length=60 # in seconds
drop_rates=(0.05 0.1 0.15 0.2)
num_nodes=(32 64 96 128)
config_name="config.json"

for num_node in ${num_nodes[@]}; do
  echo "num_node: ${num_node}"
  for drop_rate in ${drop_rates[@]}; do
    echo "drop_rate: ${drop_rate}"
    coordinate_path="topo/random${num_node}.txt"
    cd ../../Inconsistency-on-Medley/bin/
    echo "$(cat $config_name | jq --arg num_node "$num_node" '.num_server = $num_node')" > $config_name
    echo "$(cat $config_name | jq --arg coordinate_path "$coordinate_path" '.coordinate_path = $coordinate_path')" > $config_name
    echo "$(cat $config_name | jq --arg drop_rate "$drop_rate" '.msg_drop_rate = $drop_rate')" > $config_name
    ./run.sh &
    cd ../../LeaderElection/analyze/
    sleep 2
    python3 scripts/measure_inconsistency.py --drop_rate $drop_rate --num_servers $num_node &
    pid=$!
    cd ../bin/
    sleep $run_length
    kill -INT $pid
    kill $(pidof java)
  done
done
