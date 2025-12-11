package com.acertainbookstore.client.tests;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.acertainbookstore.business.Book;
import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.business.SingleLockConcurrentCertainBookStore;
import com.acertainbookstore.business.ImmutableStockBook;
import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.business.TwoLevelLockingConcurrentCertainBookStore;
import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;

/**
 * {@link BookStoreTest} tests the {@link BookStore} interface.
 * 
 * @see BookStore
 */
public class BookStoreTest {

	/** The Constant TEST_ISBN. */
	private static final int TEST_ISBN = 3044560;

	/** The Constant NUM_COPIES. */
	private static final int NUM_COPIES = 5;

	/** The local test. */
	private static boolean localTest = true;

	/** Single lock test */
	private static boolean singleLock = false;

	/** The store manager. */
	private static StockManager storeManager;

	/** The client. */
	private static BookStore client;

	/**
	 * Sets the up before class.
	 */
	@BeforeClass
	public static void setUpBeforeClass() {
		try {
			String localTestProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_LOCAL_TEST);
			localTest = (localTestProperty != null) ? Boolean.parseBoolean(localTestProperty) : localTest;
			
			String singleLockProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_SINGLE_LOCK);
			singleLock = (singleLockProperty != null) ? Boolean.parseBoolean(singleLockProperty) : singleLock;

			if (localTest) {
				if (singleLock) {
					SingleLockConcurrentCertainBookStore store = new SingleLockConcurrentCertainBookStore();
					storeManager = store;
					client = store;
				} else {
					TwoLevelLockingConcurrentCertainBookStore store = new TwoLevelLockingConcurrentCertainBookStore();
					storeManager = store;
					client = store;
				}
			} else {
				storeManager = new StockManagerHTTPProxy("http://localhost:8081/stock");
				client = new BookStoreHTTPProxy("http://localhost:8081");
			}

			storeManager.removeAllBooks();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Helper method to add some books.
	 *
	 * @param isbn
	 *            the isbn
	 * @param copies
	 *            the copies
	 * @throws BookStoreException
	 *             the book store exception
	 */
	public void addBooks(int isbn, int copies) throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		StockBook book = new ImmutableStockBook(isbn, "Test of Thrones", "George RR Testin'", (float) 10, copies, 0, 0,
				0, false);
		booksToAdd.add(book);
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Helper method to get the default book used by initializeBooks.
	 *
	 * @return the default book
	 */
	public StockBook getDefaultBook() {
		return new ImmutableStockBook(TEST_ISBN, "Harry Potter and JUnit", "JK Unit", (float) 10, NUM_COPIES, 0, 0, 0,
				false);
	}

    /**
     * Helper method to get number of copies of a single book by ISBN.
     */
    private int getNumCopies(int isbn) throws BookStoreException {
        Set<Integer> isbnSet = new HashSet<Integer>();
        isbnSet.add(isbn);
        List<StockBook> books = storeManager.getBooksByISBN(isbnSet);
        return books.get(0).getNumCopies();
    }

	/**
	 * Method to add a book, executed before every test case is run.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Before
	public void initializeBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(getDefaultBook());
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Method to clean up the book store, execute after every test case is run.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@After
	public void cleanupBooks() throws BookStoreException {
		storeManager.removeAllBooks();
	}

	/**
	 * Tests basic buyBook() functionality.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyAllCopiesDefaultBook() throws BookStoreException {
		// Set of books to buy
		Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES));

		// Try to buy books
		client.buyBooks(booksToBuy);

		List<StockBook> listBooks = storeManager.getBooks();
		assertTrue(listBooks.size() == 1);
		StockBook bookInList = listBooks.get(0);
		StockBook addedBook = getDefaultBook();

		assertTrue(bookInList.getISBN() == addedBook.getISBN() && bookInList.getTitle().equals(addedBook.getTitle())
				&& bookInList.getAuthor().equals(addedBook.getAuthor()) && bookInList.getPrice() == addedBook.getPrice()
				&& bookInList.getNumSaleMisses() == addedBook.getNumSaleMisses()
				&& bookInList.getAverageRating() == addedBook.getAverageRating()
				&& bookInList.getNumTimesRated() == addedBook.getNumTimesRated()
				&& bookInList.getTotalRating() == addedBook.getTotalRating()
				&& bookInList.isEditorPick() == addedBook.isEditorPick());
	}

	/**
	 * Tests that books with invalid ISBNs cannot be bought.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyInvalidISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with invalid ISBN.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(-1, 1)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that books can only be bought if they are in the book store.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNonExistingISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with ISBN which does not exist.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(100000, 10)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy more books than there are copies.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyTooManyBooks() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy more copies than there are in store.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES + 1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy a negative number of books.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNegativeNumberOfBookCopies() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a negative number of copies.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that all books can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetBooks() throws BookStoreException {
		Set<StockBook> booksAdded = new HashSet<StockBook>();
		booksAdded.add(getDefaultBook());

		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		booksAdded.addAll(booksToAdd);

		storeManager.addBooks(booksToAdd);

		// Get books in store.
		List<StockBook> listBooks = storeManager.getBooks();

		// Make sure the lists equal each other.
		assertTrue(listBooks.containsAll(booksAdded) && listBooks.size() == booksAdded.size());
	}

	/**
	 * Tests that a list of books with a certain feature can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetCertainBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		storeManager.addBooks(booksToAdd);

		// Get a list of ISBNs to retrieved.
		Set<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN + 1);
		isbnList.add(TEST_ISBN + 2);

		// Get books with that ISBN.
		List<Book> books = client.getBooks(isbnList);

		// Make sure the lists equal each other
		assertTrue(books.containsAll(booksToAdd) && books.size() == booksToAdd.size());
	}

	/**
	 * Tests that books cannot be retrieved if ISBN is invalid.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetInvalidIsbn() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Make an invalid ISBN.
		HashSet<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN); // valid
		isbnList.add(-1); // invalid

		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.getBooks(isbnList);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

        /**
         * Test for concurrency 1:
         * We have two threads one for each client, C1 and C2.
         * C1 calls buyBooks, C2 calls addCopies on the same book.
         * After a fixed number of operations, the number of copies must be same as before.
         */
        @Test
        public void testConcurrentBuyAndAddCopiesKeepsStock() throws Exception {
            storeManager.removeAllBooks();

            int initialCopies = 1000;
            addBooks(TEST_ISBN, initialCopies);
            final int operations = 500;

            final Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
            booksToBuy.add(new BookCopy(TEST_ISBN, 1));
            final Set<BookCopy> booksToAdd = new HashSet<BookCopy>();
            booksToAdd.add(new BookCopy(TEST_ISBN, 1));
            final boolean[] failed = new boolean[1];

            Thread buyer = new Thread(new Runnable() {
                public void run() {
                    for (int i = 0; i < operations; i++) {
                        try {
                            client.buyBooks(booksToBuy);
                        } catch (BookStoreException ex) {
                            failed[0] = true;
                            return;
                        }
                    }
                }
            });

            Thread adder = new Thread(new Runnable() {
                public void run() {
                    for (int i = 0; i < operations; i++) {
                        try {
                            storeManager.addCopies(booksToAdd);
                        } catch (BookStoreException ex) {
                            failed[0] = true;
                            return;
                        }
                    }
                }
            });

            buyer.start();
            adder.start();
            buyer.join();
            adder.join();

            assertFalse("Concurrent buy and add operations failed", failed[0]);

            int finalCopies = getNumCopies(TEST_ISBN);
            assertEquals("Final number of copies and initial should be same", initialCopies, finalCopies);
        }

        /**
         * Test for concurrency 2:
         * The writer thread repeatedly buys and replenishes a fixed collection of books,
         * while the reader thread repeatedly calls getBooksByISBN and checks snapshots are consistent.
         */
        @Test
        public void testConcurrentBuyReplenishAndGetBooksSnapshotsConsistent() throws Exception {
            storeManager.removeAllBooks();

            final int initialCopies = 10;
            final int isbn1 = TEST_ISBN;
            final int isbn2 = TEST_ISBN + 1;
            final int isbn3 = TEST_ISBN + 2;

            addBooks(isbn1, initialCopies);
            addBooks(isbn2, initialCopies);
            addBooks(isbn3, initialCopies);

            final Set<Integer> isbnSet = new HashSet<Integer>();
            isbnSet.add(isbn1);
            isbnSet.add(isbn2);
            isbnSet.add(isbn3);

            final boolean[] inconsistent = new boolean[1];

            Thread writer = new Thread(new Runnable() {
                public void run() {
                    Set<BookCopy> toBuy = new HashSet<BookCopy>();
                    toBuy.add(new BookCopy(isbn1, 1));
                    toBuy.add(new BookCopy(isbn2, 1));
                    toBuy.add(new BookCopy(isbn3, 1));

                    Set<BookCopy> toAdd = new HashSet<BookCopy>();
                    toAdd.add(new BookCopy(isbn1, 1));
                    toAdd.add(new BookCopy(isbn2, 1));
                    toAdd.add(new BookCopy(isbn3, 1));

                    for (int i = 0; i < 200; i++) {
                        try {
                            client.buyBooks(toBuy);
                            storeManager.addCopies(toAdd);
                        } catch (BookStoreException ex) {
                            inconsistent[0] = true;
                            return;
                        }
                    }
                }
            });

            Thread reader = new Thread(new Runnable() {
                public void run() {
                    for (int i = 0; i < 400; i++) {
                        try {
                            List<StockBook> snapshot = storeManager.getBooksByISBN(isbnSet);
                            if (snapshot.size() != 3) {
                                inconsistent[0] = true;
                                return;
                            }

                            int copies0 = snapshot.get(0).getNumCopies();
                            boolean allSame = true;
                            for (StockBook b : snapshot) {
                                if (b.getNumCopies() != copies0) {
                                    allSame = false;
                                    break;
                                }
                            }
                            if (!allSame) {
                                inconsistent[0] = true;
                                return;
                            }

                            if (copies0 != initialCopies && copies0 != initialCopies - 1) {
                                inconsistent[0] = true;
                                return;
                            }
                        } catch (BookStoreException ex) {
                            inconsistent[0] = true;
                            return;
                        }
                    }
                }
            });

            writer.start();
            reader.start();
            writer.join();
            reader.join();

            assertFalse("Inconsistency in book copies", inconsistent[0]);
        }

        /**
         * Concurrency test 3:
         * Two buyer threads repeatedly buy 1 copy each.
         * Final number of copies must match initialCopies - totalBuys.
         */
        @Test
        public void testConcurrentBuysNoLostUpdates() throws Exception {
            storeManager.removeAllBooks();

            final int operationsPerThread = 200;
            int initialCopies = 2 * operationsPerThread + 10;
            addBooks(TEST_ISBN, initialCopies);
            final boolean[] failed = new boolean[1];

            Runnable buyerTask = new Runnable() {
                @Override
                public void run() {
                    Set<BookCopy> toBuy = new HashSet<BookCopy>();
                    toBuy.add(new BookCopy(TEST_ISBN, 1));
                    for (int i = 0; i < operationsPerThread; i++) {
                        try {
                            client.buyBooks(toBuy);
                        } catch (BookStoreException ex) {
                            failed[0] = true;
                            return;
                        }
                    }
                }
            };

            Thread t1 = new Thread(buyerTask);
            Thread t2 = new Thread(buyerTask);

            t1.start();
            t2.start();
            t1.join();
            t2.join();

            assertFalse("Exception throwen", failed[0]);

            int finalCopies = getNumCopies(TEST_ISBN);
            int expected = initialCopies - 2 * operationsPerThread;
            assertEquals("Copies do not match after concurrent buys", expected, finalCopies);
        }

        /**
         * Concurrency test 4:
         * One thread adds new books with unique ISBNs,
         * another thread repeatedly calls getBooks().
         * We check that no exceptions or invalid data appear.
         */
        @Test
        public void testConcurrentAddBooksAndGetBooksNoExceptions() throws Exception {
            storeManager.removeAllBooks();

            final boolean[] failed = new boolean[1];
            final int writerIterations = 200;
            final int readerIterations = 400;

            Thread writer = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < writerIterations; i++) {
                        try {
                            int isbn = TEST_ISBN + i + 1000;
                            addBooks(isbn, NUM_COPIES);
                        } catch (BookStoreException ex) {
                            failed[0] = true;
                            return;
                        }
                    }
                }
            });

            Thread reader = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < readerIterations; i++) {
                        try {
                            List<StockBook> all = storeManager.getBooks();
                            for (StockBook b : all) {
                                if (b == null || b.getTitle() == null) {
                                    failed[0] = true;
                                    return;
                                }
                            }
                        } catch (BookStoreException ex) {
                            failed[0] = true;
                            return;
                        } catch (RuntimeException ex) {
                            failed[0] = true;
                            return;
                        }
                    }
                }
            });

            writer.start();
            reader.start();
            writer.join();
            reader.join();

            assertFalse("Exception or invalid data during add/getBooks", failed[0]);
        }



    /**
	 * Tear down after class.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws BookStoreException {
		storeManager.removeAllBooks();

		if (!localTest) {
			((BookStoreHTTPProxy) client).stop();
			((StockManagerHTTPProxy) storeManager).stop();
		}
	}
}
