cd ../../Skytali-$1/logs/
cd $(ls -td -- */ | head -n 1)
grep 'at time [0-9]' log.txt | tail -1