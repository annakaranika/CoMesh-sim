import os
import random
import numpy as np
from random import randint
from scipy import stats as stats

orig_devices_file = "device_location/device_location_siebel.txt"
final_path = "device_schedule/dev_sched.txt"

with open(orig_devices_file) as fp:
    lines = fp.read().splitlines()

end_time = 200000
curr_dev_states = {}
dev_modes = {}

# initialize curr_dev_states
for line in lines:
    device_data = line.split(" ")
    device = device_data[0]

    if (device_data[4] == "discrete"):
        curr_dev_states[device] = "val0"
    else:
        curr_dev_states[device] = random.uniform(0.50, 0.75)

        # record mode for thermostat
        if "tsa" in device:
            dev_modes[device] = device_data[5]
final_str = ""

# based on current value thermostat value in curr_dev_states, set a new random value within
# exponential distribution of the current value and setpoint
current_time = 0

while current_time < end_time:
    current_time += randint(500, 1000)
    chosen_device = random.choice(list(curr_dev_states.keys())) # choose random device
    print(chosen_device)
    rng = np.random.default_rng()

    # setpoint for heating: 0.75
    # setpoint for cooling 0.50
    original_val = list(curr_dev_states.keys()).index(chosen_device)
    new_val = original_val

    if "tsa" in chosen_device:
        if "val1 in dev_modes[chosen_device]":
            print("in heating")
            # heat setting: realistically value will keep rising until setpoint is reached
            print(curr_dev_states[chosen_device])
            if (curr_dev_states[chosen_device] < 0.75):
                distribution = np.logspace(np.log(curr_dev_states[chosen_device]), np.log(0.75), 10, base=np.exp(1))
                new_val = round(random.choice(distribution), 2)
                print(new_val)
                curr_dev_states[chosen_device] = new_val

        else: 
            # generate values for cooling
            print("in cooling")
            if (curr_dev_states[chosen_device] > 0.50):
                distribution = np.logspace(np.log(curr_dev_states[chosen_device]), np.log(0.50), 10, base=np.exp(1))
                new_val = round(random.choice(distribution), 2)
                curr_dev_states[chosen_device] = new_val
    else:
        new_val = "val" + str(random.randint(0, 1))

    print(new_val)

    final_str += str(round(current_time, 2)) + " " + chosen_device + " " + str(new_val) + "\n"

with open(final_path, "w") as fn_p:
    fn_p.write(final_str)