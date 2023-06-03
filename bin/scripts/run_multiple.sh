if [ ! -f 'run.sh' ]; then
  echo 'run.sh does not exist. Please run inside the bin/ folder'
  exit
fi

algos=(1 2 3 4)
num_run=100
medley_run=10
config_name="config.json"
mode="num_servers" # drop_rates, num_servers, or topologies

if [ $mode == "drop_rates" ]; then
  # different drop rates
  topologies=("random")
  num_servers=(49)
  drop_rates=(0.05 0.1 0.15 0.2)
  ks=(4 7 10 13)
elif [ $mode == "num_servers" ]; then
  # different number of servers
  topologies=("random")
  num_servers=(32 64 128 256)
  drop_rates=(0.05)
  ks=(2 5 9 17)
elif [ $mode == "topologies" ]; then
  # different topologies
  topologies=("grid" "random" "cluster")
  num_servers=(49)
  drop_rates=(0.05)
  ks=(4 4 4)
else
  echo "wrong mode!"
  exit
fi

idx=0
for topology in ${topologies[@]}; do
  echo "topology: " ${topology}
  for num_server in ${num_servers[@]}; do
    echo "num_server: ${num_server}"
    coordinate_path="topo/${topology}${num_server}.txt"
    echo "$(cat $config_name | jq --arg num_server "$num_server" '.num_servers = $num_server')" > $config_name
    echo "$(cat $config_name | jq --arg coordinate_path "$coordinate_path" '.topology_file = $coordinate_path')" > $config_name
    cd ../../Inconsistency-on-Medley/bin/
    echo "$(cat $config_name | jq --arg num_server "$num_server" '.num_server = $num_server')" > $config_name
    echo "$(cat $config_name | jq --arg coordinate_path "$coordinate_path" '.coordinate_path = $coordinate_path')" > $config_name
    cd ../../LeaderElection/bin/
    for drop_rate in ${drop_rates[@]}; do
      k=${ks[$idx]}
      echo "drop rate: ${drop_rate}"
      echo "$(cat $config_name | jq --arg drop_rate "$drop_rate" '.msg_drop_rate = $drop_rate')" > $config_name
      echo "k: ${k}"
      echo "$(cat $config_name | jq --arg k "$k" '.k = $k')" > $config_name
      for (( i=1; i<=$num_run; i++ )); do
        if (( ($i-1) % $medley_run == 0 )); then
          echo "run number: $i"
          cd ../../Inconsistency-on-Medley/bin/
          echo "$(cat $config_name | jq --arg drop_rate "$drop_rate" '.msg_drop_rate = $drop_rate')" > $config_name
          ./run.sh # run about 10 seconds
          cd ../../LeaderElection/bin/
        fi
        for algo in ${algos[@]}; do
          echo "algorithm: ${algo}"
          echo "$(cat $config_name | jq --arg algo "$algo" '.algorithm = $algo')" > $config_name
          if [ ! -d "../analyze/results/${topology}/${num_server}/${drop_rate}/algo${algo}/" ]; then
            mkdir -p "../analyze/results/${topology}/${num_server}/${drop_rate}/algo${algo}/"
          fi
          ./run.sh $config_name
          mv stats.json "../analyze/results/${topology}/${num_server}/${drop_rate}/algo${algo}/stats_${i}.json"
        done
      done
      idx=$(( $idx + 1 ))
    done
  done
done