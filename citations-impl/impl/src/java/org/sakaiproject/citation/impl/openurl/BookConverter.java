package org.sakaiproject.citation.impl.openurl;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.BidiMap;
import org.apache.commons.collections.bidimap.TreeBidiMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.citation.api.Citation;
import org.sakaiproject.citation.api.CitationService;

/**
 * Converts from a ContextObjectEntity to a Citation and back.
 * @author buckett
 *
 */
public class BookConverter implements Converter {

	private static final String ISBN_URN_PREFIX = "urn:ISBN:";

	private final static Log log = LogFactory.getLog(BookConverter.class);
	
	public final static String ID = "info:ofi/fmt:kev:mtx:book";
	
	private CitationService citationService;
	
	public void setCitationService(CitationService citationService) {
		this.citationService = citationService;
	}

	private static BidiMap conversion = new TreeBidiMap();
	static {
		// From Citation to OpenURL.
		conversion.put("creator", "au");
		conversion.put("title", "btitle");
		conversion.put("year", "date"); // TODO Should validate date (also "date", date").
		conversion.put("publisher", "pub");
		conversion.put("publicationLocation", "place");
		conversion.put("edition", "edition");
		conversion.put("sourceTitle", "series"); // Only for books, sourceTitle is used for other things.
		conversion.put("isnIdentifier", "isbn");
		// DOI and ISBN should become IDs on the context object.
	}
	
	public String getId() {
		return ID;
	}
	
	public ContextObjectEntity convert(Citation citation) {
		Map<String,Object> props = citation.getCitationProperties();
		ContextObjectEntity entity = new ContextObjectEntity();
		entity.addValue("genre", "book");
		// Loop through the citation props
		for (Map.Entry<String, Object> entry : props.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			String entityKey = (String) conversion.get(key);
			
			// If it maps to a CO property
			if (entityKey != null) {
				// TODO Not sure that citations ever uses anything other than strings.
				if (value instanceof String || value instanceof Date) {
					addValue(entity, entityKey, value.toString());
				} else if (value instanceof List) {
					// If it's multivalued add them all.
					for (String listValue : (List<String>) value) {
						addValue(entity, entityKey, listValue);
					}
				}
			} else {
				// Do other mapping.
				if ("doi".equals(key)) {
					if (value instanceof String) {
						entity.addId("info:doi"+ value);
					}
				}
			}
		}
		return entity;
	}
	
	/**
	 * Adds a value to the context object, but handles some special cases (ISBN).
	 * @param entity
	 * @param key
	 * @param value
	 */
	public void addValue(ContextObjectEntity entity, String key, String value) {
		entity.addValue(key, value);
		// Custom handling of some properties.
		if ("isbn".equals(key)) {
			entity.addId(ISBN_URN_PREFIX+ value);
		}
	}
	
	public Citation convert(ContextObjectEntity entity) {
		Map<String, List<String>> values = entity.getValues();
		
		// Set the genre.
		String genre = null;
		List<String> genres = values.get("genre");
		if (genres != null) {
			genre = genres.get(0);
		}
		if (genre == null) {
			genre = "book";
		}
		
		Citation citation = citationService.addCitation(genre);

		// Map the IDs from CO the citation.
		for (String id: entity.getIds()) {
			if (id.startsWith(ISBN_URN_PREFIX)) {
				String isbn = id.substring(ISBN_URN_PREFIX.length());
				if (isbn.length() > 0 ) {
					citation.addPropertyValue("isnIdentifier", isbn);
				}
			} else if (id.startsWith("info:doi")) {
				String doi = id.substring("info:doi".length());
				if (doi.length() > 0) {
					citation.addPropertyValue("info:doi", doi);
				}
			}
		}
		
		// Map the rest of the values.
		for(Map.Entry<String, List<String>> entry: values.entrySet()) {
			String key = entry.getKey();
			List<String> entryValues = entry.getValue();
			String citationKey = (String)conversion.getKey(key);
			if (citationKey != null) {
				if (citation.hasPropertyValue(citationKey)) {
					if (citation.isMultivalued(citationKey)) {
						for (String value: entryValues) {
							citation.addPropertyValue(citationKey, value);
						}
					} else {
						log.debug("Property already exists for: "+ citationKey);
					}
					
				} else {
					for (String value: entryValues) {
						if (value != null) {
							citation.addPropertyValue(citationKey, value);
						}
					}
				}
			} else {
				// TODO need to handle things like aufirst which don't map onto one citations value. 
			}
		}
		return citation;
	}
	
}
