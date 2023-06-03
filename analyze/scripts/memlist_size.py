
import json
NUM_NODES = 128

counter = {}
for i in range(NUM_NODES):
    path = "../../bin/membership/"+str(i)+".json"
    f = open(path)

    data = json.load(f)

    size = data["memsize"]

    if size not in counter:
        counter[size] = 1
    else:
        counter[size] += 1

    # print(size)

# print([key,value in sorted(counter)])
print(sorted(counter.items(), key=lambda x: x[0],reverse=True))
    # print("{} : {}".format(key, value))