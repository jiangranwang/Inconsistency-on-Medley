#!/bin/bash

num_server=1024
num_run=100
start=0
fail_time=100000
end_time=500000
config_name="config.json"
topo="random"
cpath=${topo}${num_server}".txt"
rpath=${topo}${num_server}"_route.txt"
metric="hop_distance"
strategies=("naive") #("naive" "active" "passive" "act_pas")
powerks=(3.0) #(0.0 0.25 0.5 0.75 1.0 1.25 1.5 1.75 2.0 2.25 2.5 2.75 3.0 3.25 3.5 3.75 4.0 4.25 4.5 4.75 5.0)

trap 'echo caught interrupt and exiting;exit' INT

if [ "${#@}" -eq 1 ]; then
  config_name=$1
fi

echo "num_server: "$num_server
echo "num_run: "$num_run
echo "fail_time: "$fail_time
echo "end_time: "$end_time
echo "config_file: "$config_name
echo "$(cat $config_name | jq --arg metric "$metric" '.distance_metric = $metric')" > $config_name
echo "$(cat $config_name | jq --arg rpath "$rpath" '.routing_path = $rpath')" > $config_name
echo "$(cat $config_name | jq --arg cpath "$cpath" '.coordinate_path = $cpath')" > $config_name
echo "$(cat $config_name | jq --arg num_run "$num_run" '.num_run = "1"')" > $config_name
echo "$(cat $config_name | jq --arg num_server "$num_server" '.num_server = $num_server')" > $config_name
echo "$(cat $config_name | jq --arg end_time "$end_time" '.end_time = $end_time')" > $config_name
echo "$(cat $config_name | jq --arg fail_time "$fail_time" '.events[0].time = $fail_time')" > $config_name
echo "$(cat $config_name | jq '.optimize_route = "true"')" > $config_name
echo "$(cat $config_name | jq '.generate_new_topology = "false"')" > $config_name
echo "$(cat $config_name | jq '.msg_drop_rate = "0.01"')" > $config_name
echo "$(cat $config_name | jq '.mobile_percentage = "0.0"')" > $config_name
echo "$(cat $config_name | jq '.mobile_distance = "0"')" > $config_name

for strategy in ${strategies[@]}; do
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

  for powerk in ${powerks[@]}; do
    echo "powerk: "$powerk
    echo "$(cat $config_name | jq --arg powerk "$powerk" '.powerk = $powerk')" > $config_name
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
      fail_node=$(( $RANDOM % 1024 ))
      echo "$(cat $config_name | jq --arg node "$fail_node" '.events[0].server = $node')" > $config_name
      echo "$(cat $config_name | jq --arg stats_path "$file_name" '.stats_path = $stats_path')" > $config_name
     ./run.sh $config_name
    done
  done
done
