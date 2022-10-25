#!/bin/bash
# Assume that there is only one action (one node fail) in config file
# This file runs tests with different powerks and different fail nodes
# supports multiple multi-runs at the same time: ./multi.sh <config file name>

test_num=23
strategy="naive"
powerks=(0.0 1.0 2.0 3.0 4.0 5.0)
end_times=(100000 100000 100000 100000 100000 100000)
suspect_times=(800 1200 1600 2000 2400)
fail_nodes=(0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24)
num_run=25

if [ ! -x "$(command -v jq)" ]; then
  echo "Please install jq."
  exit
fi

if [ "${#@}" -eq 0 ]; then
  echo "Please provide config name: ./multi.sh <config file name>"
  exit
fi

# create temporary json file
# cp config.json config_tmp.json

config_name=$@
echo $(cat $config_name | jq --arg num_run "$num_run" '.num_run = $num_run') > $config_name

len=${#powerks[@]}
for (( s=0; s<${#suspect_times[@]}; s++ )); do
  file_dir="../analyze/random_25/test_${test_num}/${strategy}/"
  (( test_num++ ))
  file_base_name="stats"
  if [ ! -d $file_dir ]; then
    mkdir -p $file_dir
  fi
  cp multi.sh $file_dir
  cp $config_name $file_dir
  suspect_time=${suspect_times[s]}
  echo 'suspect timeout: ' $suspect_time
  echo $(cat $config_name | jq --arg sus "$suspect_time" '.suspect_timeout_ms = $sus') > $config_name

  for (( i=0; i<$len; i++ )); do
    powerk=${powerks[i]}
    end_time=${end_times[i]}
    echo "powerk:" $powerk
    echo "end_time:" $end_time
    echo $(cat $config_name | jq --arg powerk "$powerk" '.powerk = $powerk') > $config_name
    echo $(cat $config_name | jq --arg end_time "$end_time" '.end_time = $end_time') > $config_name
    for fail_node in ${fail_nodes[@]}; do
      echo "fail node:" $fail_node
      file_name="${file_dir}${file_base_name}_${powerk}_${fail_node}.json"
      echo $(cat $config_name | jq --arg node "$fail_node" '.events[0].server = $node') > $config_name
      echo $(cat $config_name | jq --arg stats_path "$file_name" '.stats_path = $stats_path') > $config_name
      ./run.sh $config_name
    done
  done
done

# copy back original json file
mv config_tmp.json config.json