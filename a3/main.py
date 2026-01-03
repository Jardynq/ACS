import matplotlib.pyplot as plt
import pandas as pd

num_cores = 12
num_threads = 16

df1 = pd.read_csv('workload_metrics.csv')
df2 = pd.read_csv('workload_metrics_local.csv')
datasets = {'server': df1, 'local': df2}

runs = df1['runs'].iloc[0]
warmup = df1['runs_warmup'].iloc[0]


plt.figure(figsize=(10, 6))
for label, df in datasets.items():
    line, = plt.plot(
        df['threads'],
        df['latency[us]'],
        marker='o',
        label=f'Latency ({label})\nMean std dev: {df["latency_std"].mean():.3f} us'
    )
    plt.fill_between(
        df['threads'],
        (df['latency[us]'] - df['latency_std']).clip(lower=0),
        df['latency[us]'] + df['latency_std'],
        color=line.get_color(), alpha=0.4
    )

plt.title(f'Latency vs Threads', fontsize=14)
plt.suptitle(f'Benchmark Configuration: Runs={runs}, Warmup={warmup}', fontsize=12)
plt.xlabel('Number of Threads', fontsize=12)
plt.ylabel('Latency [us]', fontsize=12)
plt.legend()
plt.grid(True, linestyle='--', alpha=0.6)
plt.savefig('latency_plot.png')

plt.figure(figsize=(10, 6))
for label, df in datasets.items():
    line, = plt.plot(
        df['threads'],
        df['throughput[op/s]'],
        marker='s',
        label=f'Throughput ({label})\nMean std dev: {df["throughput_std"].mean():.3f} op/s'
    )
    plt.fill_between(
        df['threads'],
        (df['throughput[op/s]'] - df['throughput_std']).clip(lower=0),
        df['throughput[op/s]'] + df['throughput_std'],
        color=line.get_color(), alpha=0.4
    )

plt.title(f'Throughput vs Threads', fontsize=14)
plt.suptitle(f'Benchmark Configuration: Runs={runs}, Warmup={warmup}', fontsize=12)
plt.xlabel('Number of Threads', fontsize=12)
plt.ylabel('Throughput [op/s]', fontsize=12)
plt.legend()
plt.grid(True, linestyle='--', alpha=0.6)
plt.savefig('throughput_plot.png')
