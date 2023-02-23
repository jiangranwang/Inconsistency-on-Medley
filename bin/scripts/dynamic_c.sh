cleanup () {
  kill $(pidof java)
  kill $(pidof python3)
}
trap cleanup EXIT

num_servers=(32 64 128 256)
drop_rates=(0.1 0.2)
sample_sizes=(10 15 20)
length=100 # in seconds

for num_server in ${num_servers[@]}; do
  echo "num_server: ${num_server}"
  coordinate_path="topo/random${num_server}.txt"
  echo "$(cat config.json | jq --arg num_server "$num_server" '.num_server = $num_server')" > config.json
  echo "$(cat config.json | jq --arg coordinate_path "$coordinate_path" '.coordinate_path = $coordinate_path')" > config.json
  for drop_rate in ${drop_rates[@]}; do
    echo "drop_rate: ${drop_rate}"
    echo "$(cat config.json | jq --arg drop_rate "$drop_rate" '.msg_drop_rate = $drop_rate')" > config.json
    for sample_size in ${sample_sizes[@]}; do
      echo "sample_size: ${sample_size}"
      ./run.sh config.json &
      sleep 10
      cd ../analyze
      python3 scripts/estimate.py $num_server $drop_rate $sample_size &
      cd ../bin
      sleep $length
      cleanup
      sleep 5
    done
  done
done
