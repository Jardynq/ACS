/**
 * 
 */
package com.acertainbookstore.client.workloads;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.acertainbookstore.business.CertainBookStore;
import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;

/**
 * 
 * CertainWorkload class runs the workloads by different workers concurrently.
 * It configures the environment for the workers using WorkloadConfiguration
 * objects and reports the metrics
 * 
 */
public class CertainWorkload {
    public static String serverAddress = "http://localhost:8081";
    public static String csvPath = "workload_metrics.csv";
    public static final int maxThreads = 32;
    public static final int numWarmupRuns = 100;
    public static final int numRuns = 1000;
    public static boolean localTest = true;

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		// Initialize the RPC interfaces if its not a localTest, the variable is
		// overriden if the property is
		String localTestProperty = System
				.getProperty(BookStoreConstants.PROPERTY_KEY_LOCAL_TEST);
		localTest = (localTestProperty != null) ? Boolean
				.parseBoolean(localTestProperty) : localTest;
        if (localTest) {
            csvPath = "workload_metrics_local.csv";
        }
        try (FileWriter csvWriter = new FileWriter(csvPath)) {
            csvWriter.write("threads,runs,runs_warmup,latency[ms],latency_std,throughput[op/s],throughput_std\n"); // number of threads
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (int numThreads = 1; numThreads <= maxThreads; numThreads++) {
            List<WorkerRunResult> workerRunResults = new ArrayList<WorkerRunResult>();
            List<Future<WorkerRunResult>> runResults = new ArrayList<Future<WorkerRunResult>>();

            BookStore bookStore;
            StockManager stockManager;
            if (localTest) {
                CertainBookStore store = new CertainBookStore();
                bookStore = store;
                stockManager = store;
            } else {
                stockManager = new StockManagerHTTPProxy(serverAddress + "/stock");
                bookStore = new BookStoreHTTPProxy(serverAddress);
            }

            // Generate data in the bookstore before running the workload
            initializeBookStoreData(bookStore, stockManager);

            ExecutorService exec = Executors.newFixedThreadPool(numThreads);

            for (int i = 0; i < numThreads; i++) {
                WorkloadConfiguration config = new WorkloadConfiguration(bookStore, stockManager);
                config.setNumActualRuns(numRuns);
                config.setWarmUpRuns(numWarmupRuns);
                Worker workerTask = new Worker(config);
                // Keep the futures to wait for the result from the thread
                runResults.add(exec.submit(workerTask));
            }

            // Get the results from the threads using the futures returned
            for (Future<WorkerRunResult> futureRunResult : runResults) {
                WorkerRunResult runResult = futureRunResult.get(); // blocking call
                workerRunResults.add(runResult);
            }

            exec.shutdownNow(); // shutdown the executor

            // Finished initialization, stop the clients if not localTest
            if (!localTest) {
                assert bookStore instanceof BookStoreHTTPProxy;
                ((BookStoreHTTPProxy) bookStore).stop();
                ((StockManagerHTTPProxy) stockManager).stop();
            }

            reportMetric(workerRunResults);
        }
	}

	/**
	 * Computes the metrics and prints them
	 * 
	 * @param workerRunResults
	 */
	public static void reportMetric(List<WorkerRunResult> workerRunResults) {
        // First verify that the metrics follow the specified criteria
        // Just use asserts to crash if the criteria are not met since this is dev only
        long totalInteractions = 0;
        long totalSuccessfulInteractions = 0;
        long totalCustomerInteractions = 0;
        long totalLatencyInNanoSecs = 0L;
        double aggThroughput = 0.0;
        for (WorkerRunResult result: workerRunResults) {
            totalInteractions += result.getTotalRuns();
            totalSuccessfulInteractions += result.getSuccessfulInteractions();
            totalLatencyInNanoSecs += result.getElapsedTimeInNanoSecs();
            totalCustomerInteractions += result.getTotalFrequentBookStoreInteractionRuns();

            aggThroughput += (double)result.getSuccessfulInteractions() / (double)result.getElapsedTimeInNanoSecs();
        }
        long avgLatencyInNanoSecs = totalLatencyInNanoSecs / totalSuccessfulInteractions;

        double successRate = (totalSuccessfulInteractions * 100.0) / totalInteractions;
        assert successRate >= 99.0 : "Success rate is below 99%";

        double customerRate = (totalCustomerInteractions * 100.0) / totalInteractions;
        assert customerRate <= 62.5 && customerRate >= 57.5: "Customer interaction rate is not around 60%";

        double totalThroughputSquaredError = 0.0;
        double totalLatencySquaredError = 0.0;
        for (WorkerRunResult result: workerRunResults) {
            double threadThroughput = (double)result.getSuccessfulInteractions() / (double)result.getElapsedTimeInNanoSecs();
            double throughputDiff = threadThroughput - (aggThroughput / workerRunResults.size());
            totalThroughputSquaredError += throughputDiff * throughputDiff;

            long threadLatency = result.getElapsedTimeInNanoSecs();
            long latencyDiff = threadLatency - avgLatencyInNanoSecs;
            totalLatencySquaredError += latencyDiff * latencyDiff;
        }

        // Print the metrics as mentioned in the assigment text
        System.out.println("Number of threads: " + workerRunResults.size());
        System.out.println("Success Rate: " + successRate);
        System.out.println("Customer Rate: " + customerRate);
        System.out.println("Average Latency (us): " + Math.round(avgLatencyInNanoSecs / 1_000.0));
        System.out.println("Aggregate Throughput (ops/sec): " + Math.round(aggThroughput * 1_000_000_000.0));

        // Append to csv for plotting
        try (FileWriter csvWriter = new FileWriter(csvPath, true)) {
            csvWriter.append(String.valueOf(workerRunResults.size()));
            csvWriter.append(",");
            csvWriter.append(String.valueOf(numRuns));
            csvWriter.append(",");
            csvWriter.append(String.valueOf(numWarmupRuns));
            csvWriter.append(",");
            csvWriter.append(String.valueOf(avgLatencyInNanoSecs / 1_000_000.0));
            csvWriter.append(",");
            csvWriter.append(String.valueOf(totalLatencySquaredError / workerRunResults.size() / 1_000_000.0));
            csvWriter.append(",");
            csvWriter.append(String.valueOf(aggThroughput * 1_000_000_000.0));
            csvWriter.append(",");
            csvWriter.append(String.valueOf(totalThroughputSquaredError / workerRunResults.size() * 1_000_000_000.0));
            csvWriter.append("\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
	}

	/**
	 * Generate the data in bookstore before the workload interactions are run
	 * 
	 * Ignores the serverAddress if its a localTest
	 * 
	 */
	public static void initializeBookStoreData(BookStore bookStore,
			StockManager stockManager) throws BookStoreException {
        bookStore = bookStore;
        stockManager = stockManager;



	}
}
