import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import sys
import plot_setting

LEGEND_NAME = {
    'leader': 'leader_only',
    'member': 'member_only',
    'non_idle': 'leader/member',
    'idle': 'idle'
}

# Draw CDF with values (bin_count) and the counts of values (count)
def drawCDF(count, bins_count, legend='default'):
    pdf = count / sum(count)
    cdf = np.cumsum(pdf)
    plt.plot(bins_count, cdf, label=legend)
    plt.legend()

# Get and draw CDF with 1D numpy array of data
def getCDF(data, legend):
    count, bins_count = np.histogram(data, bins=10000)
    count = np.concatenate([[0], count])
    drawCDF(count, bins_count, legend)

def pltSetting(ylabel='CDF',
               xlabel='Time units'):
    plt.ylim(0, 1)
    plt.ylabel(ylabel)
    plt.xlabel(xlabel)
    plt.tight_layout()

def plotCrossKGroups(df, figname='balance_cross.png'):
    getCDF(df['leader'].to_list(), LEGEND_NAME['leader'])
    getCDF(df['member'].to_list(), LEGEND_NAME['member'])
    getCDF(df['ld_and_mem'].to_list(), LEGEND_NAME['non_idle'])
    pltSetting()
    plt.xlim(-20000, 550000)
    plt.savefig(figname)
    print(figname)
    plt.clf()

def plotSingular(df, figname='balance_singular.png'):
    getCDF(df['sing_leader'].to_list(), LEGEND_NAME['leader'])
    getCDF(df['sing_member'].to_list(), LEGEND_NAME['member'])
    getCDF(df['sing_idle'].to_list(), LEGEND_NAME['idle'])
    pltSetting()
    plt.savefig(figname)
    plt.clf()

def plotRoleCount(fname, figname):
    for role in ['leader', 'member', 'non_idle']:
        data_fname = fname[:-4] + '_' + role + '.csv'
        df = pd.read_csv(data_fname)
        drawCDF(np.array(df['time'].to_list()),
                np.array(df['num_role'].to_list()),
                LEGEND_NAME[role])
    plt.xlim(right=44)

    pltSetting(xlabel='Number of concurrent k-groups')
    plt.savefig(figname)
    plt.clf()


if __name__ == "__main__":
    fname = sys.argv[1]
    df = pd.read_csv(fname)

    figname = 'outputs/balance/cross_balance' + fname.split('balance')[2][:-4] + '.png'
    plotCrossKGroups(df, figname)

    figname = 'outputs/balance/singl_balance' + fname.split('balance')[2][:-4] + '.png'
    plotSingular(df, figname)

    figname = 'outputs/balance/role_balance' + fname.split('balance')[2][:-4] + '.png'
    plotRoleCount(fname, figname)

