#!/bin/bash
# Assume that there is only one action (one node fail) in config file
# This file runs tests with different powerks and different fail nodes
# supports multiple multi-runs at the same time: ./random.sh <config file name>

num_server=49
num_run=25
fail_time=180000
end_time=300000
config_name="config.json"
topology="random" # random or unlucky or cluster
metric="hop_distance"
strategies=("naive") #("naive" "active" "passive" "act_pas")
powerks=(3.0) #(0.0 0.25 0.5 0.75 1.0 1.25 1.5 1.75 2.0 2.25 2.5 2.75 3.0 3.25 3.5 3.75 4.0 4.25 4.5 4.75 5.0)
fail_nodes=$(seq 0 $(( num_server-1 )))

trap 'echo caught interrupt and exiting;exit' INT

if [ ! -x "$(command -v jq)" ]; then
  echo "Please install jq."
  exit
fi
if [ "${#@}" -eq 1 ]; then
  config_name=$1
fi

echo "num_server: "$num_server
echo "num_run: "$num_run
echo "fail_time: "$fail_time
echo "end_time: "$end_time
echo "topology: "$topology
echo "config_file: "$config_name
echo "$(cat $config_name | jq --arg topology "$topology" '.topology_type = $topology')" > $config_name
echo "$(cat $config_name | jq --arg num_run "$num_run" '.num_run = $num_run')" > $config_name
echo "$(cat $config_name | jq --arg num_server "$num_server" '.num_server = $num_server')" > $config_name
echo "$(cat $config_name | jq --arg end_time "$end_time" '.end_time = $end_time')" > $config_name
echo "$(cat $config_name | jq --arg fail_time "$fail_time" '.events[0].time = $fail_time')" > $config_name
echo "$(cat $config_name | jq --arg metric "$metric" '.distance_metric = $metric')" > $config_name
echo "$(cat $config_name | jq '.events[0].mode = "shutdown"')" > $config_name
echo "$(cat $config_name | jq '.verbose = "1"')" > $config_name
echo "$(cat $config_name | jq '.generate_new_topology = "true"')" > $config_name
if [ $num_server -eq 16 ]; then
  length=10
elif [ $num_server -eq 49 ]; then
  length=15
elif [ $num_server -eq 100 ]; then
  length=20
fi
echo "$(cat $config_name | jq --arg length "$length" '.length = $length')" > $config_name

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
    file_dir="../analyze/all_${topology}_${num_server}/${powerk}/${strategy}/"
    if [ ! -d $file_dir ]; then
      mkdir -p $file_dir
    fi
    if [ ! -d ${file_dir}topo/ ]; then
      mkdir -p ${file_dir}topo/
    fi
    cp random.sh $file_dir
    cp $config_name $file_dir
    powerk=${powerks[i]}

    for fail_node in ${fail_nodes[@]}; do
      echo "fail_node: "$fail_node
      file_name="${file_dir}${fail_node}.json"
      echo "$(cat $config_name | jq --arg node "$fail_node" '.events[0].server = $node')" > $config_name
      echo "$(cat $config_name | jq --arg stats_path "$file_name" '.stats_path = $stats_path')" > $config_name
      echo "$(cat $config_name | jq --arg cpath "${file_dir}topo/random.txt" '.coordinate_path = $cpath')" > $config_name
      ./run.sh $config_name
      if [ $fail_node -eq 0 ]; then
        echo "plotting graphs for coordinates"
        for ((j=1; j<=$num_run; j++ )); do
          python3 graph.py --file ${file_dir}topo/random_${j}.txt --size $length
        done
      fi
    done
  done
done