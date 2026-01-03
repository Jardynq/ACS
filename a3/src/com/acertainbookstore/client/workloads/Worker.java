/**
 * 
 */
package com.acertainbookstore.client.workloads;

import java.util.*;
import java.util.concurrent.Callable;

import com.acertainbookstore.business.Book;
import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.utils.BookStoreException;

/**
 * 
 * Worker represents the workload runner which runs the workloads with
 * parameters using WorkloadConfiguration and then reports the results
 * 
 */
public class Worker implements Callable<WorkerRunResult> {
    private WorkloadConfiguration configuration = null;
    private int numSuccessfulFrequentBookStoreInteraction = 0;
    private int numTotalFrequentBookStoreInteraction = 0;

    public Worker(WorkloadConfiguration config) {
	    configuration = config;
    }

    /**
     * Run the appropriate interaction while trying to maintain the configured
     * distributions
     * 
     * Updates the counts of total runs and successful runs for customer
     * interaction
     * 
     * @param chooseInteraction
     * @return
     */
    private boolean runInteraction(float chooseInteraction) {
        try {
            float percentRareStockManagerInteraction = configuration.getPercentRareStockManagerInteraction();
            float percentFrequentStockManagerInteraction = configuration.getPercentFrequentStockManagerInteraction();

            if (chooseInteraction < percentRareStockManagerInteraction) {
                runRareStockManagerInteraction();
            } else if (chooseInteraction < percentRareStockManagerInteraction + percentFrequentStockManagerInteraction) {
                runFrequentStockManagerInteraction();
            } else {
                numTotalFrequentBookStoreInteraction++;
                runFrequentBookStoreInteraction();
                numSuccessfulFrequentBookStoreInteraction++;
            }
        } catch (BookStoreException ex) {
            return false;
        }
        return true;
    }

    /**
     * Run the workloads trying to respect the distributions of the interactions
     * and return result in the end
     */
    public WorkerRunResult call() throws Exception {
        int count = 1;
        long startTimeInNanoSecs = 0;
        long endTimeInNanoSecs = 0;
        int successfulInteractions = 0;
        long timeForRunsInNanoSecs = 0;

        Random rand = new Random();
        float chooseInteraction;

        // Perform the warmup runs
        while (count++ <= configuration.getWarmUpRuns()) {
            chooseInteraction = rand.nextFloat() * 100f;
            runInteraction(chooseInteraction);
        }

        count = 1;
        numTotalFrequentBookStoreInteraction = 0;
        numSuccessfulFrequentBookStoreInteraction = 0;

        // Perform the actual runs
        startTimeInNanoSecs = System.nanoTime();
        while (count++ <= configuration.getNumActualRuns()) {
            chooseInteraction = rand.nextFloat() * 100f;
            if (runInteraction(chooseInteraction)) {
                successfulInteractions++;
            }
        }
        endTimeInNanoSecs = System.nanoTime();
        timeForRunsInNanoSecs += (endTimeInNanoSecs - startTimeInNanoSecs);
        return new WorkerRunResult(successfulInteractions, timeForRunsInNanoSecs, configuration.getNumActualRuns(),
            numSuccessfulFrequentBookStoreInteraction, numTotalFrequentBookStoreInteraction);
    }

    /**
     * Runs the new stock acquisition interaction
     * 
     * @throws BookStoreException
     */
    private void runRareStockManagerInteraction() throws BookStoreException {
        List<StockBook> existingBooks = configuration.getStockManager().getBooks();

        Set<Integer> existingIsbns = new HashSet<>();
        for (StockBook book : existingBooks) {
            existingIsbns.add(book.getISBN());
        }
        Set<StockBook> newBooks = configuration.getBookSetGenerator()
                .nextSetOfStockBooks(configuration.getNumBooksToAdd());

        Set<StockBook> booksToAdd = new HashSet<>();
        for (StockBook book : newBooks) {
            if (!existingIsbns.contains(book.getISBN())) {
                booksToAdd.add(book);
            }
        }

        if (!booksToAdd.isEmpty()) {
            configuration.getStockManager().addBooks(booksToAdd);
        }
    }

    /**
     * Runs the stock replenishment interaction
     * 
     * @throws BookStoreException
     */
    private void runFrequentStockManagerInteraction() throws BookStoreException {
        List<StockBook> books = configuration.getStockManager().getBooks();
        List<StockBook> sortedBooks = new ArrayList<>(books);
        sortedBooks.sort(Comparator.comparingInt(StockBook::getNumCopies));

        int k = Math.min(configuration.getNumBooksWithLeastCopies(), sortedBooks.size());
        int addCopies = configuration.getNumAddCopies();

        Set<BookCopy> booksToReplenish = new HashSet<>();
        for (int i = 0; i < k; i++) {
            int isbn = sortedBooks.get(i).getISBN();
            booksToReplenish.add(new BookCopy(isbn, addCopies));
        }

        if (!booksToReplenish.isEmpty()) {
            configuration.getStockManager().addCopies(booksToReplenish);
        }
    }

    /**
     * Runs the customer interaction
     * 
     * @throws BookStoreException
     */
    private void runFrequentBookStoreInteraction() throws BookStoreException {
	    List<Book> books = configuration.getBookStore().getEditorPicks(configuration.getNumEditorPicksToGet());
        if (books.isEmpty()) {
            return;
        }

        Set<Integer> isbnsToBuy = new HashSet<>();
        for (Book book : books) {
            isbnsToBuy.add(book.getISBN());
        }

        int wantedBooks = configuration.getNumBooksToBuy();
        int canBuy = Math.min(wantedBooks, isbnsToBuy.size());

        Set<Integer> sampledIsbns = configuration.getBookSetGenerator()
            .sampleFromSetOfISBNs(isbnsToBuy, canBuy);

        int copiesPerBook = configuration.getNumBookCopiesToBuy();
        Set<BookCopy> booksToBuy = new HashSet<>();
        for (int isbn : sampledIsbns) {
            booksToBuy.add(new BookCopy(isbn, copiesPerBook));
        }

        if (!booksToBuy.isEmpty()) {
            configuration.getBookStore().buyBooks(booksToBuy);
        }
    }

}
