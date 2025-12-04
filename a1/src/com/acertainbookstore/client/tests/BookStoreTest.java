package com.acertainbookstore.client.tests;

import com.acertainbookstore.business.*;
import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;
import org.junit.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * {@link BookStoreTest} tests the {@link BookStore} interface.
 *
 * @see BookStore
 */
public class BookStoreTest {

    /**
     * The Constant TEST_ISBN.
     */
    private static final int TEST_ISBN = 3044560;

    /**
     * The Constant NUM_COPIES.
     */
    private static final int NUM_COPIES = 5;

    /**
     * The local test.
     */
    private static boolean localTest = false;

    /**
     * The store manager.
     */
    private static StockManager storeManager;

    /**
     * The client.
     */
    private static BookStore client;

    /**
     * Sets the up before class.
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        try {
            String localTestProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_LOCAL_TEST);
            localTest = (localTestProperty != null) ? Boolean.parseBoolean(localTestProperty) : localTest;

            if (localTest) {
                CertainBookStore store = new CertainBookStore();
                storeManager = store;
                client = store;
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
     * Tear down after class.
     *
     * @throws BookStoreException the book store exception
     */
    @AfterClass
    public static void tearDownAfterClass() throws BookStoreException {
        storeManager.removeAllBooks();

        if (!localTest) {
            ((BookStoreHTTPProxy) client).stop();
            ((StockManagerHTTPProxy) storeManager).stop();
        }
    }

    /**
     * Helper method to add some books.
     *
     * @param isbn   the isbn
     * @param copies the copies
     * @throws BookStoreException the book store exception
     */
    public void addBooks(int isbn, int copies) throws BookStoreException {
        Set<StockBook> booksToAdd = new HashSet<>();
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
     * Method to add a book, executed before every test case is run.
     *
     * @throws BookStoreException the book store exception
     */
    @Before
    public void initializeBooks() throws BookStoreException {
        Set<StockBook> booksToAdd = new HashSet<>();
        booksToAdd.add(getDefaultBook());
        storeManager.addBooks(booksToAdd);
    }

    /**
     * Method to clean up the book store, execute after every test case is run.
     *
     * @throws BookStoreException the book store exception
     */
    @After
    public void cleanupBooks() throws BookStoreException {
        storeManager.removeAllBooks();
    }

    /**
     * Tests basic buyBook() functionality.
     *
     * @throws BookStoreException the book store exception
     */
    @Test
    public void testBuyAllCopiesDefaultBook() throws BookStoreException {
        // Set of books to buy
        Set<BookCopy> booksToBuy = new HashSet<>();
        booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES));

        // Try to buy books
        client.buyBooks(booksToBuy);

        List<StockBook> listBooks = storeManager.getBooks();
        assertEquals(1, listBooks.size());
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
     * @throws BookStoreException the book store exception
     */
    @Test
    public void testBuyInvalidISBN() throws BookStoreException {
        List<StockBook> booksInStorePreTest = storeManager.getBooks();

        // Try to buy a book with invalid ISBN.
        HashSet<BookCopy> booksToBuy = new HashSet<>();
        booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
        booksToBuy.add(new BookCopy(-1, 1)); // invalid

        // Try to buy the books.
        try {
            client.buyBooks(booksToBuy);
            fail();
        } catch (BookStoreException ex) {
        }

        List<StockBook> booksInStorePostTest = storeManager.getBooks();

        // Check pre and post state are same.
        assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
                && booksInStorePreTest.size() == booksInStorePostTest.size());
    }

    /**
     * Tests that books can only be bought if they are in the book store.
     *
     * @throws BookStoreException the book store exception
     */
    @Test
    public void testBuyNonExistingISBN() throws BookStoreException {
        List<StockBook> booksInStorePreTest = storeManager.getBooks();

        // Try to buy a book with ISBN which does not exist.
        HashSet<BookCopy> booksToBuy = new HashSet<>();
        booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
        booksToBuy.add(new BookCopy(100000, 10)); // invalid

        // Try to buy the books.
        try {
            client.buyBooks(booksToBuy);
            fail();
        } catch (BookStoreException ex) {
        }

        List<StockBook> booksInStorePostTest = storeManager.getBooks();

        // Check pre and post state are same.
        assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
                && booksInStorePreTest.size() == booksInStorePostTest.size());
    }

    /**
     * Tests that you can't buy more books than there are copies.
     *
     * @throws BookStoreException the book store exception
     */
    @Test
    public void testBuyTooManyBooks() throws BookStoreException {
        List<StockBook> booksInStorePreTest = storeManager.getBooks();

        // Try to buy more copies than there are in store.
        HashSet<BookCopy> booksToBuy = new HashSet<>();
        booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES + 1));

        try {
            client.buyBooks(booksToBuy);
            fail();
        } catch (BookStoreException ex) {
        }

        List<StockBook> booksInStorePostTest = storeManager.getBooks();
        assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
                && booksInStorePreTest.size() == booksInStorePostTest.size());
    }

    /**
     * Tests that you can't buy a negative number of books.
     *
     * @throws BookStoreException the book store exception
     */
    @Test
    public void testBuyNegativeNumberOfBookCopies() throws BookStoreException {
        List<StockBook> booksInStorePreTest = storeManager.getBooks();

        // Try to buy a negative number of copies.
        HashSet<BookCopy> booksToBuy = new HashSet<>();
        booksToBuy.add(new BookCopy(TEST_ISBN, -1));

        try {
            client.buyBooks(booksToBuy);
            fail();
        } catch (BookStoreException ignored) {
        }

        List<StockBook> booksInStorePostTest = storeManager.getBooks();
        assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
                && booksInStorePreTest.size() == booksInStorePostTest.size());
    }

    /**
     * Tests that all books can be retrieved.
     *
     * @throws BookStoreException the book store exception
     */
    @Test
    public void testGetBooks() throws BookStoreException {
        Set<StockBook> booksAdded = new HashSet<>();
        booksAdded.add(getDefaultBook());

        Set<StockBook> booksToAdd = new HashSet<>();
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
     * @throws BookStoreException the book store exception
     */
    @Test
    public void testGetCertainBooks() throws BookStoreException {
        Set<StockBook> booksToAdd = new HashSet<>();
        booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
                (float) 300, NUM_COPIES, 0, 0, 0, false));
        booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
                "Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

        storeManager.addBooks(booksToAdd);

        // Get a list of ISBNs to retrieved.
        Set<Integer> isbnList = new HashSet<>();
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
     * @throws BookStoreException the book store exception
     */
    @Test
    public void testGetInvalidIsbn() throws BookStoreException {
        List<StockBook> booksInStorePreTest = storeManager.getBooks();

        // Make an invalid ISBN.
        HashSet<Integer> isbnList = new HashSet<>();
        isbnList.add(TEST_ISBN); // valid
        isbnList.add(-1); // invalid

        try {
            client.getBooks(isbnList);
            fail();
        } catch (BookStoreException ex) {
        }

        List<StockBook> booksInStorePostTest = storeManager.getBooks();
        assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
                && booksInStorePreTest.size() == booksInStorePostTest.size());
    }

    /**
     * Tests input validation for rateBooks.
     * Ensures that invalid ratings are rejected and valid ratings are accepted.
     * Ensures that invalid ISBNs are rejected.
     *
     * @throws BookStoreException the book store exception
     */
    @Test
    public void testRateBookValidation() throws BookStoreException {
        List<StockBook> booksInStorePreTest = storeManager.getBooks();

        HashSet<BookRating> ratingsToAdd = new HashSet<>();
        ratingsToAdd.add(new BookRating(-1, 4)); // invalid
        try {
            client.rateBooks(ratingsToAdd);
            fail();
        } catch (BookStoreException ex) {
        }

        ratingsToAdd.clear();
        ratingsToAdd.add(new BookRating(TEST_ISBN, 6)); // invalid
        try {
            client.rateBooks(ratingsToAdd);
            fail();
        } catch (BookStoreException ex) {
        }

        ratingsToAdd.clear();
        ratingsToAdd.add(new BookRating(TEST_ISBN, -1)); // invalid
        try {
            client.rateBooks(ratingsToAdd);
            fail();
        } catch (BookStoreException ex) {
        }

        ratingsToAdd.clear();
        ratingsToAdd.add(new BookRating(TEST_ISBN, 0)); // valid
        ratingsToAdd.add(new BookRating(TEST_ISBN, 1)); // valid
        ratingsToAdd.add(new BookRating(TEST_ISBN, 2)); // valid
        ratingsToAdd.add(new BookRating(TEST_ISBN, 3)); // valid
        ratingsToAdd.add(new BookRating(TEST_ISBN, 4)); // valid
        ratingsToAdd.add(new BookRating(TEST_ISBN, 5)); // valid

        client.rateBooks(ratingsToAdd);

        List<StockBook> booksInStorePostTest = storeManager.getBooks();
        assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
                && booksInStorePreTest.size() == booksInStorePostTest.size());
    }

    /**
     * Tests input validation for getTopRatedBooks.
     * Ensures that negative numBooks are rejected.
     * Ensures that numBooks > totalBooks is truncated.
     *
     * @throws BookStoreException the book store exception
     */
    @Test
    public void testGetTopRatedBooksValidation() throws BookStoreException {
        addBooks(1, 10);
        addBooks(2, 10);
        addBooks(3, 10);
        List<StockBook> booksInStorePreTest = storeManager.getBooks();

        try {
            client.getTopRatedBooks(-1);
            fail();
        } catch (BookStoreException ex) {
        }

        var books = client.getTopRatedBooks(10);
        assertEquals(4, books.size());

        List<StockBook> booksInStorePostTest = storeManager.getBooks();
        assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
                && booksInStorePreTest.size() == booksInStorePostTest.size());
    }

    /**
     * Tests that books cannot be retrieved if ISBN is invalid.
     *
     * @throws BookStoreException the book store exception
     */
    @Test
    public void testAddRatingsAndGetTopRatedBooks() throws BookStoreException {
        addBooks(1, 10);
        addBooks(2, 10);
        addBooks(3, 10);
        List<StockBook> booksInStorePreTest = storeManager.getBooks();

        HashSet<BookRating> ratingsToAdd = new HashSet<>();
        ratingsToAdd.add(new BookRating(TEST_ISBN, 3));
        ratingsToAdd.add(new BookRating(1, 1));
        ratingsToAdd.add(new BookRating(2, 2));

        client.rateBooks(ratingsToAdd);
        var books = client.getTopRatedBooks(4).stream().map(Book::getISBN);
        assertArrayEquals(new Integer[]{3, 1, 2, TEST_ISBN}, books.toArray());

        ratingsToAdd.clear();
        ratingsToAdd.add(new BookRating(TEST_ISBN, 0));

        client.rateBooks(ratingsToAdd);
        var books2 = client.getTopRatedBooks(4).stream().map(Book::getISBN);
        assertArrayEquals(new Integer[]{3, 1, TEST_ISBN, 2}, books2.toArray());

        List<StockBook> booksInStorePostTest = storeManager.getBooks();
        assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
                && booksInStorePreTest.size() == booksInStorePostTest.size());
    }
}
