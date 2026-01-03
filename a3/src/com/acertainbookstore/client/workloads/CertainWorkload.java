/**
 * 
 */
package com.acertainbookstore.client.workloads;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.acertainbookstore.business.BookEditorPick;
import com.acertainbookstore.business.BookRating;
import com.acertainbookstore.business.CertainBookStore;
import com.acertainbookstore.business.StockBook;
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
    public static final int numRuns = 5000;
    public static final int initialBooks = 10;
    public static boolean localTest = false;

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
            csvWriter.write("threads,runs,runs_warmup,latency[us],latency_std,throughput[op/s],throughput_std\n"); // number of threads
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
                ((BookStoreHTTPProxy) bookStore).restartServer();
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
        long count = workerRunResults.size();
        long totalInteractions = 0;
        long totalSuccessfulInteractions = 0;
        long totalCustomerInteractions = 0;
        long totalSuccessfulCustomerInteractions = 0;
        long totalElapsedTime = 0;
        double aggThroughput = 0.0;
        for (WorkerRunResult result: workerRunResults) {
            totalInteractions += result.getTotalRuns();
            totalSuccessfulInteractions += result.getSuccessfulInteractions();
            totalCustomerInteractions += result.getTotalFrequentBookStoreInteractionRuns();
            totalSuccessfulCustomerInteractions += result.getSuccessfulFrequentBookStoreInteractionRuns();
            totalElapsedTime += result.getElapsedTimeInNanoSecs();

            aggThroughput += (double)result.getSuccessfulFrequentBookStoreInteractionRuns()
                    / (double)result.getElapsedTimeInNanoSecs();
        }
        double avgLatency = (double)totalElapsedTime / (double)totalSuccessfulCustomerInteractions;

        double successRate = (totalSuccessfulInteractions * 100.0) / totalInteractions;
        assert successRate >= 99.0 : "Success rate is below 99%";

        double customerRate = (totalCustomerInteractions * 100.0) / totalInteractions;
        assert customerRate <= 62.5 && customerRate >= 57.5: "Customer interaction rate is not around 60%";

        double totalThroughputSquaredError = 0.0;
        double totalLatencySquaredError = 0.0;
        for (WorkerRunResult result: workerRunResults) {
            var dt = (double)result.getElapsedTimeInNanoSecs();
            var si = (double)result.getSuccessfulFrequentBookStoreInteractionRuns();

            double threadThroughput = si / dt;
            double throughputDiff = threadThroughput - (aggThroughput / count);
            totalThroughputSquaredError += (throughputDiff * throughputDiff) / count;

            double threadLatency = dt / si;
            double latencyDiff = threadLatency - avgLatency;
            totalLatencySquaredError += (latencyDiff * latencyDiff) / count;
        }
        double latencyStdDev = Math.sqrt(totalLatencySquaredError);
        double throughputStdDev = Math.sqrt(totalThroughputSquaredError);

        // Print the metrics as mentioned in the assigment text
        System.out.println("Number of threads: " + workerRunResults.size());
        System.out.println("Success Rate: " + successRate);
        System.out.println("Customer Rate: " + customerRate);
        System.out.println("Average Latency (us): " + Math.round(avgLatency / 1_000.0));
        System.out.println("Average Latency std. dev.: " + Math.round(latencyStdDev / 1_000.0));
        System.out.println("Aggregate Throughput (ops/sec): " + Math.round(aggThroughput * 1_000_000_000.0));
        System.out.println("Aggregate Throughput std. dev.: " + Math.round(throughputStdDev * 1_000_000_000.0));

        // Append to csv for plotting
        try (FileWriter csvWriter = new FileWriter(csvPath, true)) {
            csvWriter.append(String.valueOf(workerRunResults.size()));
            csvWriter.append(",");
            csvWriter.append(String.valueOf(numRuns));
            csvWriter.append(",");
            csvWriter.append(String.valueOf(numWarmupRuns));
            csvWriter.append(",");
            csvWriter.append(String.valueOf(avgLatency / 1_000.0));
            csvWriter.append(",");
            csvWriter.append(String.valueOf(latencyStdDev / 1_000.0));
            csvWriter.append(",");
            csvWriter.append(String.valueOf(aggThroughput * 1_000_000_000.0));
            csvWriter.append(",");
            csvWriter.append(String.valueOf(throughputStdDev * 1_000_000_000.0));
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
        var rand = new java.util.Random(42);
        var bookSetGenerator = new BookSetGenerator(42);
        var booksToAdd = bookSetGenerator.nextSetOfStockBooks(initialBooks);
        var picks = new HashSet<BookEditorPick>();
        for (StockBook book : booksToAdd) {
            picks.add(new BookEditorPick(book.getISBN(), rand.nextBoolean()));
        }
        stockManager.addBooks(booksToAdd);
        stockManager.updateEditorPicks(picks);
    }
}
