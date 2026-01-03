package com.acertainbookstore.client.workloads;

import java.util.Set;
import java.util.HashSet;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import com.acertainbookstore.business.ImmutableStockBook;
import com.acertainbookstore.business.StockBook;

/**
 * Helper class to generate stockbooks and isbns modelled similar to Random
 * class
 */
public class BookSetGenerator {

    private final Random random;
    private int nextIsbn;

    public BookSetGenerator() {
        this.random = new Random();
        this.nextIsbn = 10000;
    }

    /**
     * Returns num randomly selected isbns from the input set
     *
     * @param num
     * @return
     */
    public Set<Integer> sampleFromSetOfISBNs(Set<Integer> isbns, int num) {
        if (num < 0 || num > isbns.size()) {
            throw new IllegalArgumentException("num must be between 0 and the size of isbns");
        }

        List<Integer> list = new ArrayList<>(isbns);
        Collections.shuffle(list, random);

        return new HashSet<>(list.subList(0, num));
    }

    /**
     * Return num stock books. For now return an ImmutableStockBook
     *
     * @param num
     * @return
     */
    public Set<StockBook> nextSetOfStockBooks(int num) {
        if (num < 0) {
            throw new IllegalArgumentException("num must be 0 or more");
        }

        Set<StockBook> stockBooks = new HashSet<>();
        while (stockBooks.size() < num) {
            int isbn = nextIsbn++;
            String title = "Title" + isbn;
            String author = "Author" + isbn;
            float price = 50 + random.nextInt(201);
            int numCopies = 1 + random.nextInt(20);

            long numSaleMisses = 0;
            long numTimesRated = 0;
            long totalRating = 0;
            boolean editorPick = false;

            stockBooks.add(new ImmutableStockBook(isbn, title, author, price, numCopies, numSaleMisses,
                    numTimesRated, totalRating, editorPick));
        }
        return stockBooks;
    }
}
