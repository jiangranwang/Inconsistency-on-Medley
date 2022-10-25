#!/bin/bash
# simulates domain failure: specify the failure servers in each cluster
# This file runs tests with different powerks and different fail nodes
# supports multiple multi-runs at the same time: ./multi.sh <config file name>

test_num=18
strategy="naive"
powerks=(0.0 0.5 1.0 1.5 2.0 2.5 3.0 3.5 4.0 4.5 5.0)
end_times=(50000 50000 50000 50000 50000 50000 50000 50000 100000 100000 100000)
cluster_1=(0 1 2 3 4 5)
cluster_2=(6 7 8 9 10 11 12 13 14)
cluster_3=(15 16 17 18 19 20 21 22 23 24)
num_run=100

if [ ! -x "$(command -v jq)" ]; then
  echo "Please install jq."
  exit
fi

if [ "${#@}" -eq 0 ]; then
  echo "Please provide config name: ./multi.sh <config file name>"
  exit
fi

# create temporary json file
cp config.json config_tmp.json

file_dir="../analyze/random_25/test_${test_num}/${strategy}/"
file_base_name="stats"
config_name=$@
if [ ! -d $file_dir ]; then
  mkdir -p $file_dir
fi
cp domain.sh $file_dir
cp $config_name $file_dir

echo "$(cat $config_name | jq --arg num_run "$num_run" '.num_run = $num_run')" > $config_name

powerk_len=${#powerks[@]}

for (( i=0; i<$powerk_len; i++ )); do
  powerk=${powerks[i]}
  end_time=${end_times[i]}
  echo "powerk:" $powerk
  echo "end_time:" $end_time
  echo "$(cat $config_name | jq --arg powerk "$powerk" '.powerk = $powerk')" > $config_name
  echo "$(cat $config_name | jq --arg end_time "$end_time" '.end_time = $end_time')" > $config_name

  # cluster_1 failure
  echo "$(cat $config_name | jq 'del(.events)')" > $config_name
  echo "$(cat $config_name | jq '.events = []')" > $config_name
  cluster_len=${#cluster_1[@]}
  for (( j=0; j<$cluster_len; j++ )); do
    fail_node=${cluster_1[j]}
    echo "$(cat $config_name | jq --arg node "$fail_node" '.events += [{server: $node, time: "5000", mode: "shutdown"}]')" > $config_name
  done
  echo "cluster_1"
  file_name="${file_dir}${file_base_name}_${powerk}_cluster_1.json"
  echo "$(cat $config_name | jq --arg stats_path "$file_name" '.stats_path = $stats_path')" > $config_name
  ./run.sh $config_name

  # cluster_2 failure
  echo "$(cat $config_name | jq 'del(.events)')" > $config_name
  echo "$(cat $config_name | jq '.events = []')" > $config_name
  cluster_len=${#cluster_2[@]}
  for (( j=0; j<$cluster_len; j++ )); do
    fail_node=${cluster_2[j]}
    echo "$(cat $config_name | jq --arg node "$fail_node" '.events += [{server: $node, time: "5000", mode: "shutdown"}]')" > $config_name
  done
  echo "cluster_2"
  file_name="${file_dir}${file_base_name}_${powerk}_cluster_2.json"
  echo "$(cat $config_name | jq --arg stats_path "$file_name" '.stats_path = $stats_path')" > $config_name
  ./run.sh $config_name

  # cluster_3 failure
  echo "$(cat $config_name | jq 'del(.events)')" > $config_name
  echo "$(cat $config_name | jq '.events = []')" > $config_name
  cluster_len=${#cluster_3[@]}
  for (( j=0; j<$cluster_len; j++ )); do
    fail_node=${cluster_3[j]}
    echo "$(cat $config_name | jq --arg node "$fail_node" '.events += [{server: $node, time: "5000", mode: "shutdown"}]')" > $config_name
  done
  echo "cluster_3"
  file_name="${file_dir}${file_base_name}_${powerk}_cluster_3.json"
  echo "$(cat $config_name | jq --arg stats_path "$file_name" '.stats_path = $stats_path')" > $config_name
  ./run.sh $config_name
done

# copy back original json file
mv config_tmp.json config.json
