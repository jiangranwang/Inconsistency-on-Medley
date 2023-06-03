#!/bin/bash

# cd ../bin

config_name="config.json"
# drop_rates=(0.95 0.9)
# drop_rates=(0.1)
drop_rates=(0.3 0.2 0.1)

echo '' > test.txt
for drop_rate in ${drop_rates[@]}; do
    # contents="$(jq '.msg_drop_rate = $drop_rate' $config_name)" && \
    tmp="$(mktemp)"
    jq --arg drop_rate "$drop_rate" '.msg_drop_rate = $drop_rate' $config_name > "$tmp"
    mv "$tmp" $config_name
    # echo -E "${contents}"
    # echo -E "${contents}" > $config_name
    # echo "$(cat $config_name | jq --arg drop_rate "$drop_rate" '.msg_drop_rate = $drop_rate')" > $config_name
    echo "drop rate: ${drop_rate}"

    for i in {1..16}; do
        echo "$i"
        ../bin/run.sh config.json
    done
done
