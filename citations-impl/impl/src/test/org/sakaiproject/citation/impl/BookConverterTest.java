package org.sakaiproject.citation.impl;

import org.sakaiproject.citation.api.Citation;
import org.sakaiproject.citation.api.CitationService;
import org.sakaiproject.citation.api.Schema;
import org.sakaiproject.citation.impl.openurl.BookConverter;
import org.sakaiproject.citation.impl.openurl.ContextObjectEntity;

public class BookConverterTest extends BaseCitationServiceSupport {

	private static final String DOI = "/doi/value";
	private static final String BOOK_SERIES = "A seris of books";
	private static final String ISBN = "1234567890";
	private static final String PUBLISHER = "A publisher of books.";
	private static final String DATE = "2008-08-08";
	private static final String AUTHOR = "An Author";
	private static final String TITLE = "A Book Title";

	public void testSimpleBookConverstion() throws Exception {
		CitationService citationService = createCitationService();
		BookConverter converter = new BookConverter();
		converter.setCitationService(citationService);
		
		Citation book = citationService.addCitation("book");
		book.addPropertyValue(Schema.TITLE, TITLE);
		book.addPropertyValue(Schema.CREATOR, AUTHOR);
		book.addPropertyValue(Schema.YEAR, DATE);
		book.addPropertyValue(Schema.PUBLISHER, PUBLISHER);
		book.addPropertyValue(Schema.ISN, ISBN);
		book.addPropertyValue(Schema.SOURCE_TITLE, BOOK_SERIES);
		book.addPropertyValue("doi", DOI);
		
		ContextObjectEntity bookContextObject = converter.convert(book);
		assertEquals(TITLE, bookContextObject.getValue("btitle"));
		assertEquals(AUTHOR, bookContextObject.getValue("au"));
		assertEquals(DATE, bookContextObject.getValue("date"));
		assertEquals(PUBLISHER, bookContextObject.getValue("pub"));
		assertEquals(ISBN, bookContextObject.getValue("isbn"));
		assertEquals(BOOK_SERIES, bookContextObject.getValue("series"));
		assertTrue(bookContextObject.getIds().contains("info:doi"+ DOI));
	}
	
}
