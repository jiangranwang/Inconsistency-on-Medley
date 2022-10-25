#!/bin/bash

start=0
num_servers=(128 256 512 1024)
num_runs=(1000 1000 1000 1000)
end_times=(35000 40000 45000 50000)
cache_sizes=(128 256 500 500)
cache_timeouts=(15000 30000 60000 120000)
fail_time=100000
config_name="config.json"
topo="random"
metric="hop_distance"
strategy="naive"
powerk=3.0

trap 'echo caught interrupt and exiting;exit' INT

if [ "${#@}" -eq 1 ]; then
  config_name=$1
fi

echo "$(cat $config_name | jq --arg metric "$metric" '.distance_metric = $metric')" > $config_name
echo "$(cat $config_name | jq --arg fail_time "$fail_time" '.events[0].time = $fail_time')" > $config_name
echo "$(cat $config_name | jq --arg powerk "$powerk" '.powerk = $powerk')" > $config_name
echo "$(cat $config_name | jq '.optimize_route = "true"')" > $config_name
echo "$(cat $config_name | jq '.generate_new_topology = "false"')" > $config_name
echo "$(cat $config_name | jq '.msg_drop_rate = "0.01"')" > $config_name
echo "$(cat $config_name | jq '.mobile_percentage = "0.0"')" > $config_name
echo "$(cat $config_name | jq '.mobile_distance = "0"')" > $config_name
echo "fail_time: "$fail_time
echo "powerk: "$powerk
echo "config_file: "$config_name
echo "strategy: "$strategy
if [ "$strategy" = "naive" ]; then
  echo "$(cat $config_name | jq '.strategy = "naive_bag"')" > $config_name
elif [ "$strategy" = "active" ]; then
  echo "$(cat $config_name | jq '.strategy = "active_feedback_bag"')" > $config_name
elif [ "$strategy" = "passive" ]; then
  echo "$(cat $config_name | jq '.strategy = "passive_feedback_bag"')" > $config_name
elif [ "$strategy" = "act_pas" ]; then
  echo "$(cat $config_name | jq '.strategy = "act_pas_feedback_bag"')" > $config_name
else
  echo "strategy not valid"
  exit
fi

for i in "${!num_servers[@]}"; do
  num_server=${num_servers[$i]}
  num_run=${num_runs[$i]}
  end_time=${end_times[$i]}
  cache_size=${cache_sizes[$i]}
  cache_timeout=${cache_timeouts[$i]}
  echo "num_server: "$num_server
  echo "num_run: "$num_run
  echo "end_time: "$end_time
  echo "cache_size: "$cache_size
  echo "cache_timeout: "$cache_timeout

  cpath=${topo}${num_server}".txt"
  rpath=${topo}${num_server}"_route.txt"
  echo "$(cat $config_name | jq --arg rpath "$rpath" '.routing_path = $rpath')" > $config_name
  echo "$(cat $config_name | jq --arg cpath "$cpath" '.coordinate_path = $cpath')" > $config_name
  echo "$(cat $config_name | jq --arg num_run "$num_run" '.num_run = "1"')" > $config_name
  echo "$(cat $config_name | jq --arg num_server "$num_server" '.num_server = $num_server')" > $config_name
  echo "$(cat $config_name | jq --arg end_time "$end_time" '.end_time = $end_time')" > $config_name
  echo "$(cat $config_name | jq --arg cache_size "$cache_size" '.cache_size = $cache_size')" > $config_name
  echo "$(cat $config_name | jq --arg cache_timeout "$cache_timeout" '.cache_timeout = $cache_timeout')" > $config_name

  file_dir="../analyze/${topo}_${num_server}/${powerk}/${strategy}/"
  if [ ! -d $file_dir ]; then
    mkdir -p $file_dir
  fi
  cp large.sh $file_dir
  cp $config_name $file_dir
  for i in $(seq $num_run); do
    i=$(( i+$start ))
    echo "run_num: "$i
    file_name="${file_dir}${i}.json"
    fail_node=$(( $RANDOM % $num_server ))
    echo "$(cat $config_name | jq --arg node "$fail_node" '.events[0].server = $node')" > $config_name
    echo "$(cat $config_name | jq --arg stats_path "$file_name" '.stats_path = $stats_path')" > $config_name
   ./run.sh $config_name
  done
done
