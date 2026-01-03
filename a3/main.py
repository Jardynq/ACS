import matplotlib.pyplot as plt
import pandas as pd

num_cores = 12
num_threads = 16

df1 = pd.read_csv('workload_metrics.csv')
df2 = pd.read_csv('workload_metrics_local.csv')
datasets = {'metrics': df1, 'local': df2}

runs = df1['runs'].iloc[0]
warmup = df1['runs_warmup'].iloc[0]

plt.figure(figsize=(10, 6))
for label, df in datasets.items():
    line, = plt.plot(df['threads'], df['latency[ms]'], marker='o', label=f'Latency ({label})')
    plt.fill_between(
        df['threads'],
        df['latency[ms]'] - df['latency_std'],
        df['latency[ms]'] + df['latency_std'],
        color=line.get_color(), alpha=0.2
    )

plt.title(f'Latency vs Threads')
plt.suptitle(f'Benchmark Configuration: Runs={runs}, Warmup={warmup}', fontsize=10, y=0.92)
plt.xlabel('Number of Threads')
plt.ylabel('Latency [ms]')
plt.legend()
plt.grid(True, linestyle='--', alpha=0.6)
plt.savefig('latency_plot.png')

plt.figure(figsize=(10, 6))
for label, df in datasets.items():
    line, = plt.plot(df['threads'], df['throughput[op/s]'], marker='s', label=f'Throughput ({label})')
    plt.fill_between(
        df['threads'],
        df['throughput[op/s]'] - df['throughput_std'],
        df['throughput[op/s]'] + df['throughput_std'],
        color=line.get_color(), alpha=0.2
    )

plt.title(f'Throughput vs Threads')
plt.suptitle(f'Benchmark Configuration: Runs={runs}, Warmup={warmup}', fontsize=10, y=0.92)
plt.xlabel('Number of Threads')
plt.ylabel('Throughput [op/s]')
plt.legend()
plt.grid(True, linestyle='--', alpha=0.6)
plt.savefig('throughput_plot.png')
