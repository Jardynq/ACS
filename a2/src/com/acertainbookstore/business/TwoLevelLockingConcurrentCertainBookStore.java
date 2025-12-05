package com.acertainbookstore.business;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.stream.Collectors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;
import com.acertainbookstore.utils.BookStoreUtility;

/**
 * {@link LockedBookStoreBook} wraps a {@link BookStoreBook} and provides synchronization
 * capabilities.
 */
class LockedBookStoreBook extends BookStoreBook implements ReadWriteLock {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    public LockedBookStoreBook(StockBook book) {
        super(book);
    }
    public ReentrantReadWriteLock.ReadLock readLock() { return lock.readLock(); }
    public ReentrantReadWriteLock.WriteLock writeLock() { return lock.writeLock(); }
}

/** {@link TwoLevelLockingConcurrentCertainBookStore} implements the {@link BookStore} and
 * {@link StockManager} functionalities.
 * 
 * @see BookStore
 * @see StockManager
 */
public class TwoLevelLockingConcurrentCertainBookStore implements BookStore, StockManager {

	/** The mapping of books from ISBN to {@link LockedBookStoreBook}. */
	private Map<Integer, LockedBookStoreBook> bookMap = null;

    /** The coarse grained lock for the bookstore. */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	/**
	 * Instantiates a new {@link CertainBookStore}.
	 */
	public TwoLevelLockingConcurrentCertainBookStore() {
		// Constructors are not synchronized
		bookMap = new HashMap<>();
	}
	
	private void validate(StockBook book) throws BookStoreException {
		int isbn = book.getISBN();
		String bookTitle = book.getTitle();
		String bookAuthor = book.getAuthor();
		int noCopies = book.getNumCopies();
		float bookPrice = book.getPrice();

		if (BookStoreUtility.isInvalidISBN(isbn)) { // Check if the book has valid ISBN
			throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isEmpty(bookTitle)) { // Check if the book has valid title
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isEmpty(bookAuthor)) { // Check if the book has valid author
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isInvalidNoCopies(noCopies)) { // Check if the book has at least one copy
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (bookPrice < 0.0) { // Check if the price of the book is valid
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (bookMap.containsKey(isbn)) {// Check if the book is not in stock
			throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.DUPLICATED);
		}
	}	
	
	private void validate(BookCopy bookCopy) throws BookStoreException {
		int isbn = bookCopy.getISBN();
		int numCopies = bookCopy.getNumCopies();

		validateISBNInStock(isbn); // Check if the book has valid ISBN and in stock

		if (BookStoreUtility.isInvalidNoCopies(numCopies)) { // Check if the number of the book copy is larger than zero
			throw new BookStoreException(BookStoreConstants.NUM_COPIES + numCopies + BookStoreConstants.INVALID);
		}
	}
	
	private void validate(BookEditorPick editorPickArg) throws BookStoreException {
		int isbn = editorPickArg.getISBN();
		validateISBNInStock(isbn); // Check if the book has valid ISBN and in stock
	}
	
	private void validateISBNInStock(Integer ISBN) throws BookStoreException {
		if (BookStoreUtility.isInvalidISBN(ISBN)) { // Check if the book has valid ISBN
			throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
		}
		if (!bookMap.containsKey(ISBN)) {// Check if the book is in stock
			throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#addBooks(java.util.Set)
	 */
	public void addBooks(Set<StockBook> bookSet) throws BookStoreException {
		if (bookSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		// Check if all are there
        lock.writeLock().lock();
		for (StockBook book : bookSet) {
            try {
                validate(book);
            } catch (BookStoreException e) {
                lock.writeLock().unlock();
                throw e;
            }
		}

		for (StockBook book : bookSet) {
			int isbn = book.getISBN();
			bookMap.put(isbn, new LockedBookStoreBook(book));
		}
        lock.writeLock().unlock();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#addCopies(java.util.Set)
	 */
	public void addCopies(Set<BookCopy> bookCopiesSet) throws BookStoreException {
		if (bookCopiesSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

        List<BookCopy> sortedBookCopies = new ArrayList<>(bookCopiesSet);
        sortedBookCopies.sort(Comparator.comparingInt(BookCopy::getISBN));

        lock.readLock().lock();
		for (BookCopy bookCopy : sortedBookCopies) {
            try {
                validate(bookCopy);
            } catch (BookStoreException e) {
                lock.readLock().unlock();
                throw e;
            }
		}

		// Update the number of copies
        List<Lock> locks = new ArrayList<>();
		for (BookCopy bookCopy : sortedBookCopies) {
			var book = bookMap.get(bookCopy.getISBN());
            var lock = book.writeLock();
            locks.add(lock);
            lock.lock();
			book.addCopies(bookCopy.getNumCopies());
		}
        for (var lock : locks) {
            lock.unlock();
        }
        lock.readLock().unlock();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#getBooks()
	 */
	public List<StockBook> getBooks() {
        lock.readLock().lock();
        var result = bookMap.values().stream()
                .map(book -> {
                    book.readLock().lock();
                    StockBook immutableBook = book.immutableStockBook();
                    book.readLock().unlock();
                    return immutableBook;
                })
                .collect(Collectors.toList());
        lock.readLock().unlock();
        return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#updateEditorPicks(java.util
	 * .Set)
	 */
	public void updateEditorPicks(Set<BookEditorPick> editorPicks) throws BookStoreException {
		// Check that all ISBNs that we add/remove are there first.
		if (editorPicks == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

        List<BookEditorPick> sortedEditorPicks = new ArrayList<>(editorPicks);
        sortedEditorPicks.sort(Comparator.comparingInt(BookEditorPick::getISBN));

        lock.readLock().lock();
		for (BookEditorPick editorPickArg : sortedEditorPicks) {
            try {
                validate(editorPickArg);
            } catch (BookStoreException e) {
                lock.readLock().unlock();
                throw e;
            }
		}

        List<Lock> locks = new ArrayList<>();
		for (BookEditorPick editorPickArg : sortedEditorPicks) {
			var book = bookMap.get(editorPickArg.getISBN());
            var lock = book.writeLock();
            locks.add(lock);
            lock.lock();
            book.setEditorPick(editorPickArg.isEditorPick());
		}
        for (var lock : locks) {
            lock.unlock();
        }
        lock.readLock().unlock();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#buyBooks(java.util.Set)
	 */
	public void buyBooks(Set<BookCopy> bookCopiesToBuy) throws BookStoreException {
		if (bookCopiesToBuy == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		// Check that all ISBNs that we buy are there first.
		int isbn;
		LockedBookStoreBook book;
		Boolean saleMiss = false;

		Map<Integer, Integer> salesMisses = new HashMap<>();

        List<Lock> locks = new ArrayList<>();

        lock.readLock().lock();
		for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
            try {
                validate(bookCopyToBuy);
            } catch (BookStoreException e) {
                for (var lock : locks) {
                    lock.unlock();
                }
                lock.readLock().unlock();
                throw e;
            }

            isbn = bookCopyToBuy.getISBN();
			book = bookMap.get(isbn);
            var lock = book.writeLock();
            locks.add(lock);
            lock.lock();
			if (!book.areCopiesInStore(bookCopyToBuy.getNumCopies())) {
				// If we cannot sell the copies of the book, it is a miss.
				salesMisses.put(isbn, bookCopyToBuy.getNumCopies() - book.getNumCopies());
				saleMiss = true;
			}
		}

		// We throw exception now since we want to see how many books in the
		// order incurred misses which is used by books in demand
		if (saleMiss) {
			for (Map.Entry<Integer, Integer> saleMissEntry : salesMisses.entrySet()) {
				book = bookMap.get(saleMissEntry.getKey());
				book.addSaleMiss(saleMissEntry.getValue());
			}
            for (var lock : locks) {
                lock.unlock();
            }
            lock.readLock().unlock();
			throw new BookStoreException(BookStoreConstants.BOOK + BookStoreConstants.NOT_AVAILABLE);
		}

		// Then make the purchase.
		for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
			book = bookMap.get(bookCopyToBuy.getISBN());
			book.buyCopies(bookCopyToBuy.getNumCopies());
		}
        for (var lock : locks) {
            lock.unlock();
        }
        lock.readLock().unlock();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#getBooksByISBN(java.util.
	 * Set)
	 */
	public List<StockBook> getBooksByISBN(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

        lock.readLock().lock();
		for (Integer ISBN : isbnSet) {
            try {
                validateISBNInStock(ISBN);
            } catch (BookStoreException e) {
                lock.readLock().unlock();
                throw e;
            }
		}

		var result = isbnSet.stream()
				.map(isbn -> {
                    var book = bookMap.get(isbn);
                    book.readLock().lock();
                    StockBook immutableBook = book.immutableStockBook();
                    book.readLock().unlock();
                    return immutableBook;
                })
				.collect(Collectors.toList());
        lock.readLock().unlock();
        return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getBooks(java.util.Set)
	 */
	public List<Book> getBooks(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		// Check that all ISBNs that we rate are there to start with.
        lock.readLock().lock();
		for (Integer ISBN : isbnSet) {
            try {
                validateISBNInStock(ISBN);
            } catch (BookStoreException e) {
                lock.readLock().unlock();
                throw e;
            }
		}

		var result = isbnSet.stream()
				.map(isbn -> {
                    var book = bookMap.get(isbn);
                    book.readLock().lock();
                    Book immutableBook = book.immutableBook();
                    book.readLock().unlock();
                    return immutableBook;
                })
				.collect(Collectors.toList());
        lock.readLock().unlock();
        return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getEditorPicks(int)
	 */
	public List<Book> getEditorPicks(int numBooks) throws BookStoreException {
		if (numBooks < 0) {
			throw new BookStoreException("numBooks = " + numBooks + ", but it must be positive");
		}

        lock.readLock().lock();
		List<BookStoreBook> listAllEditorPicks = bookMap.entrySet().stream()
				.map(pair -> pair.getValue())
				.filter(book -> {
                    book.readLock().lock();
                    boolean isEditorPick = book.isEditorPick();
                    book.readLock().unlock();
                    return isEditorPick;
                })
				.collect(Collectors.toList());
        lock.readLock().unlock();

		// Find numBooks random indices of books that will be picked.
		Random rand = new Random();
		Set<Integer> tobePicked = new HashSet<>();
		int rangePicks = listAllEditorPicks.size();

		if (rangePicks <= numBooks) {

			// We need to add all books.
			for (int i = 0; i < listAllEditorPicks.size(); i++) {
				tobePicked.add(i);
			}
		} else {

			// We need to pick randomly the books that need to be returned.
			int randNum;

			while (tobePicked.size() < numBooks) {
				randNum = rand.nextInt(rangePicks);
				tobePicked.add(randNum);
			}
		}

		// Return all the books by the randomly chosen indices.
		return tobePicked.stream()
				.map(index -> listAllEditorPicks.get(index).immutableBook())
				.collect(Collectors.toList());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getTopRatedBooks(int)
	 */
	@Override
	public List<Book> getTopRatedBooks(int numBooks) throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#getBooksInDemand()
	 */
	@Override
	public List<StockBook> getBooksInDemand() throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#rateBooks(java.util.Set)
	 */
	@Override
	public void rateBooks(Set<BookRating> bookRating) throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#removeAllBooks()
	 */
	public void removeAllBooks() throws BookStoreException {
        lock.writeLock().lock();
		bookMap.clear();
        lock.writeLock().unlock();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#removeBooks(java.util.Set)
	 */
	public void removeBooks(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

        lock.writeLock().lock();
		for (Integer ISBN : isbnSet) {
			if (BookStoreUtility.isInvalidISBN(ISBN)) {
                lock.writeLock().unlock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
			}

			if (!bookMap.containsKey(ISBN)) {
                lock.writeLock().unlock();
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
			}
		}

		for (int isbn : isbnSet) {
			bookMap.remove(isbn);
		}
        lock.writeLock().unlock();
	}
}
