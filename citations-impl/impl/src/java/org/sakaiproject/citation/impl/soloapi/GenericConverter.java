package org.sakaiproject.citation.impl.soloapi;

import org.sakaiproject.citation.api.Citation;

import java.util.ArrayList;


/**
 * Converts from a Context to a Citation.
 * @author nickwilson
 *
 */
public class GenericConverter extends AbstractConverter  {

	public String getType() {
		return "GEN";
	}

	@Override
	public Citation convert(ContextObject context) {
		Citation citation = super.convert(context);

		setCitationProperty(citation, primoNMBibNode, "creator", new ArrayList<String>(){{  add("record"); add("addata"); add("au");}} );
		setCitationProperty(citation, primoNMBibNode, "creator", new ArrayList<String>(){{  add("record"); add("addata"); add("addau");}} );

		setCitationProperty(citation, primoNMBibNode, "title", new ArrayList<String>(){{  add("record"); add("addata"); add("btitle");}} );
		if (citation.getCitationProperty("title", false)==null || citation.getCitationProperty("title", false).equals("")){
			setCitationProperty(citation, primoNMBibNode, "title", new ArrayList<String>(){{  add("record"); add("addata"); add("atitle");}} );
		}
		setCitationProperty(citation, primoNMBibNode, "sourceTitle", new ArrayList<String>(){{  add("record"); add("addata"); add("jtitle");}} );

		setCitationProperty(citation, primoNMBibNode, "date", new ArrayList<String>(){{  add("record"); add("addata"); add("date");}} );
		setCitationProperty(citation, primoNMBibNode, "volume", new ArrayList<String>(){{  add("record"); add("addata"); add("volume");}} );
		setCitationProperty(citation, primoNMBibNode, "issue", new ArrayList<String>(){{  add("record"); add("addata"); add("issue");}} );
		setCitationProperty(citation, primoNMBibNode, "pages", new ArrayList<String>(){{  add("record"); add("addata"); add("pages");}} );
		setCitationProperty(citation, primoNMBibNode, "startPage", new ArrayList<String>(){{  add("record"); add("addata"); add("spage");}} );
		setCitationProperty(citation, primoNMBibNode, "endPage", new ArrayList<String>(){{  add("record"); add("addata"); add("epage");}} );

		setCitationProperty(citation, primoNMBibNode, "isnIdentifier", new ArrayList<String>(){{ add("record"); add("addata"); add("issn"); }} );
		if (citation.getCitationProperty("isnIdentifier")==null || citation.getCitationProperty("isnIdentifier").equals("")){
			setCitationProperty(citation, primoNMBibNode, "isnIdentifier", new ArrayList<String>(){{ add("record"); add("addata"); add("isbn"); }} );
		}
		if (citation.getCitationProperty("isnIdentifier")==null || citation.getCitationProperty("isnIdentifier").equals("")){
			setCitationProperty(citation, primoNMBibNode, "isnIdentifier", new ArrayList<String>(){{ add("record"); add("addata"); add("eissn"); }} );
		}

		return citation;
	}
}
