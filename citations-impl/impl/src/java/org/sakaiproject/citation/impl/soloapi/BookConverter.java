package org.sakaiproject.citation.impl.soloapi;

import org.sakaiproject.citation.api.Citation;

import java.util.ArrayList;

/**
 * Converts from a Context to a Citation.
 * @author nickwilson
 *
 */
public class BookConverter extends AbstractConverter {

	public String getType() {
		return "BOOK";
	}

	@Override
	public Citation convert(ContextObject context) {
		Citation citation = super.convert(context);

		setCitationProperty(citation, primoNMBibNode, "creator", new ArrayList<String>(){{  add("record"); add("addata"); add("au");}} );
		if (citation.getCitationProperty("creator")!=null && !citation.getCitationProperty("creator").equals("")){
			setCitationProperty(citation, primoNMBibNode, "creator", new ArrayList<String>(){{  add("record"); add("addata"); add("addau"); }} );
		}
		else {
			setCitationProperty(citation, primoNMBibNode, "editor", new ArrayList<String>(){{  add("record"); add("addata"); add("addau"); }} );
		}
		setCitationProperty(citation, primoNMBibNode, "title", new ArrayList<String>(){{  add("record"); add("addata"); add("btitle");}} );
		setCitationProperty(citation, primoNMBibNode, "sourceTitle", new ArrayList<String>(){{  add("addata"); add("seriestitle"); }} );
		setCitationProperty(citation, primoNMBibNode, "isnIdentifier", new ArrayList<String>(){{ add("record"); add("addata"); add("isbn"); }} );

		return citation;
	}
}
