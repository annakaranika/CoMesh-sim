###
# Scripts to plot bandwidth checking
# Usage:
#      python3 bw_verification.py <bw_filename> [num_seed]
# where `bw_filename' contains the bandwidth data for the whole run
# (instead of specific checkpoint), and `num_seed' is the total number
# of the seed. If `num_seed' is provided, the script will generate the
# cdf that aggregates data from seed 0 to `num_seed'. Otherwise, the
# script will generate the cdf of a specific run (based on bw_filename)
###

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import sys
import os
import plot_setting

def getCDF(data, legend):
    count, bins_count = np.histogram(data, bins=10000)
    pdf = count / sum(count)
    cdf = np.cumsum(pdf)
    cdf = np.concatenate([[0], cdf])
    plt.plot(bins_count, cdf, label=legend)
    plt.legend()

def pltSetting():
    plt.ylim(0, 1)
    plt.ylabel('CDF')
    plt.xlabel('Number of Messages')
    plt.tight_layout()

# Normalize the message list by msg / actual_runtime * target_runtime.
# This is needed since each checkpoint has different runtime.
def getNormedMsgOverTime(msgs, actual_runtime, target_runtime=1000):
    return np.array(msgs) * (1.0 * target_runtime / actual_runtime)

# Extract the running time of a specific checkpoint.
# Information recorded in the last line of checkpoint log.
def getCheckpointTime(fname):
    with open(fname, "r") as fin:
        return int(fin.readlines()[-1].split(' ')[-1])

# Get the file name for the last checkpoint.
# The last checkpoint includes the message records after all routines are done.
# TODO: Needs to check the code logic, since there might appear > 3 checkpoints
#       even when there is one routine. (once appears up to 8 checkpoints.)
def getFinishingRecordFname(fname):
    dir = os.path.dirname(fname)
    basename = os.path.basename(fname)
    prefix = basename[:-4]
    last_fname = ''
    for path, currentDirectory, files in os.walk(dir):
        for file in files:
            if file != basename and file.startswith(prefix) and file > last_fname:
                last_fname = file
    return dir + os.path.sep + last_fname

# Plot background bandwidth CDF (both end-to-end and hop-by-hop) for
# 1) before any routine starts, and 2) after all routines finish.
def plotBGBandwith(df_bf, bf_time,
                   df_bh, bh_time,
                   figname='bg_check.png'):
    if bf_time > 0:
        getCDF(getNormedMsgOverTime(df_bf['E2E_BG'].to_list(), bf_time), 'before_routine_E2E_BG')
        getCDF(getNormedMsgOverTime(df_bf['H2H_BG'].to_list(), bf_time), 'before_routine_H2H_BG')
    if bh_time > 0:
        getCDF(getNormedMsgOverTime(df_bh['E2E_BG'].to_list(), bh_time), 'after_routine_E2E_BG')
        getCDF(getNormedMsgOverTime(df_bh['H2H_BG'].to_list(), bh_time), 'after_routine_H2H_BG')

    pltSetting()
    plt.savefig(figname)
    plt.clf()

# Get prefix of filename. Here, prefix is defined with no random seed number and
# no file extension (e.g. csv)
def getFilePrefix(fname):
    return '_'.join(fname.split('_')[:-1])


def getNormedMsgOverTimeByFilename(fname):
    time = getCheckpointTime(fname)
    if time == 0:
        return [], []

    df = pd.read_csv(fname, skipfooter=1)
    e2e_bw = getNormedMsgOverTime(df['E2E_BG'].to_list(), time)
    h2h_bw = getNormedMsgOverTime(df['H2H_BG'].to_list(), time)
    return e2e_bw, h2h_bw


# Generate plots for aggregated data from seed 0 - num_seed
def plotAggregatedBandwidth(prefix, num_seed, figname='bg_check_aggregated.png'):
    e2e_bf = []
    e2e_bh = []
    h2h_bf = []
    h2h_bh = []

    for seed in range(num_seed):
        fname_bf = prefix + '_' + str(seed) + '_0.csv'
        fname_bh = getFinishingRecordFname(prefix + '_' + str(seed) + '.csv')

        e2e, h2h = getNormedMsgOverTimeByFilename(fname_bf)
        e2e_bf.extend(e2e)
        h2h_bf.extend(h2h)

        e2e, h2h = getNormedMsgOverTimeByFilename(fname_bh)
        e2e_bh.extend(e2e)
        h2h_bh.extend(h2h)

    getCDF(e2e_bf, 'before_routine_E2E_BG')
    getCDF(h2h_bf, 'before_routine_H2H_BG')
    getCDF(e2e_bh, 'after_routine_E2E_BG')
    getCDF(h2h_bh, 'after_routine_H2H_BG')

    pltSetting()
    plt.savefig(figname)
    plt.clf()

if __name__ == "__main__":
    fname = sys.argv[1]

    # Request for aggregated cdf.
    if len(sys.argv) > 2:
        num_seed = int(sys.argv[2])
        prefix = getFilePrefix(fname)
        figname = 'outputs/bandwidth_bg/bg_aggr' + fname.split('bandwidth_bg')[2][:-4] + '.png'
        plotAggregatedBandwidth(prefix, num_seed, figname)

    # Request for cdf for a single run.
    fname_bf = fname[:-4] + '_0' + fname[-4:]
    df_bf = pd.read_csv(fname_bf, skipfooter=1)
    fname_bh = getFinishingRecordFname(fname)
    df_bh = pd.read_csv(fname_bh, skipfooter=1)

    figname = 'outputs/bandwidth_bg/bg_check' + fname.split('bandwidth_bg')[2][:-4] + '.png'
    plotBGBandwith(df_bf, getCheckpointTime(fname_bf),
                   df_bh, getCheckpointTime(fname_bh),
                   figname)
