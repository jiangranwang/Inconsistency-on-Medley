#!/bin/bash
# simulates simultaneous failure: specify the maximum number of simultaneous failures and number of test for each
# number of simultaneous failure
# This file runs tests with different powerks and different fail nodes
# supports multiple multi-runs at the same time: ./multi.sh <config file name>

test_num=22
strategy="naive"
powerks=(0.0 1.5 3.0)
end_times=(20000 20000 20000)
max_num_fail=12
per_num_run=1000
num_run=1

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
cp simul.sh $file_dir
cp $config_name $file_dir

echo $(cat $config_name | jq --arg num_run "$num_run" '.num_run = $num_run') > $config_name

powerk_len=${#powerks[@]}

for (( i=0; i<$powerk_len; i++ )); do
  powerk=${powerks[i]}
  end_time=${end_times[i]}
  echo "powerk:" $powerk
  echo "end_time:" $end_time
  echo "$(cat $config_name | jq --arg powerk "$powerk" '.powerk = $powerk')" > $config_name
  echo "$(cat $config_name | jq --arg end_time "$end_time" '.end_time = $end_time')" > $config_name

  for (( j=1; j<=$max_num_fail; j++)); do
    for (( num=0; num<$per_num_run; num++ )); do
      echo "$(cat $config_name | jq 'del(.events)')" > $config_name
      echo "$(cat $config_name | jq '.events = []')" > $config_name
      fail_nodes=( $( shuf -i 0-24 -n $j ) )
      for (( k=0; k<$j; k++ )); do
        fail_node=${fail_nodes[k]}
        echo "$(cat $config_name | jq --arg node "$fail_node" '.events += [{server: $node, time: "5000", mode: "shutdown"}]')" > $config_name
      done
      echo $num
      file_name="${file_dir}${file_base_name}_${powerk}_${j}fail_${num}.json"
      echo "$(cat $config_name | jq --arg stats_path "$file_name" '.stats_path = $stats_path')" > $config_name
      ./run.sh $config_name
    done
  done
done

# copy back original json file
mv config_tmp.json config.json
