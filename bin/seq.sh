timeout=(0 20000 25000 30000 35000 40000)
for (( i=1; i<${#timeout[@]}; i++ )) do
  echo 'timeout: '${timeout[i]}
  echo "$(cat config.json | jq --arg to "${timeout[i]}" '.passive_feedback_timeout = $to')" > config.json
  ./multi.sh $i 'naive'
  ./multi.sh $i 'active'
  ./multi.sh $i 'passive'
  ./multi.sh $i 'act_pas'
  python3 ../analyze/test.py --path ../analyze/random_16/test_$i
done