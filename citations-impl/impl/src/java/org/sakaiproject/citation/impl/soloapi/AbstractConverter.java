package org.sakaiproject.citation.impl.soloapi;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.sakaiproject.citation.api.Citation;
import org.sakaiproject.citation.api.CitationService;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;

public abstract class AbstractConverter implements Converter {

	private CitationService citationService;

	protected JsonNode primoNMBibNode;
	protected JsonNode docNode;
	protected JsonNode getItNode;

	public void setCitationService(CitationService citationService) {
		this.citationService = citationService;
	}

	protected void setCitationProperty(Citation citation, JsonNode primoNMBibNode, String citationKey, ArrayList<String> jsonPath) {

		// find the json node
		for (String jsonNode : jsonPath) {
			primoNMBibNode = primoNMBibNode.get(jsonNode);
			if (primoNMBibNode==null) {
				break;
			}
		}

		if (primoNMBibNode!=null){
			String value = null;

			// if jsonnode contains array
			if (primoNMBibNode instanceof ArrayNode){
				for (JsonNode jsonNode : primoNMBibNode) {
					value = jsonNode.getTextValue();
					if (value != null) {
						citation.setCitationProperty(citationKey, value);
					}
				}
			}
			else {
				value = primoNMBibNode.getTextValue();
				if (value != null) {
					value = formatSpecialCases(citationKey, value);
					citation.setCitationProperty(citationKey, value);
				}
			}

		}
	}

	private String formatSpecialCases(String citationKey, String value) {

		// if it's a year we get the first four digits of the date
		if (citationKey.equals("year") && value.length()>3) {
			value = value.substring(0,4);
		}
		// to get the link to solo we use hte parameter from the the open url,
		else if (citationKey.equals("otherIds") && value.startsWith("http://oxfordsfx")) {
			for (String string : value.split("&")) {
				if (string.startsWith("rft_id=http") && string.contains("solo.bodleian")){
					value = string.split("=")[1];
					try {
						value = URLDecoder.decode(value, "UTF-8");
					} catch (UnsupportedEncodingException ex) {
						throw new RuntimeException("Could not decode rft_id value which is:" + value);
					}
				}
			}
		}
		return value;
	}

	public Citation convert(ContextObject context) {
		docNode = context.getNode().get("SEGMENTS").get("JAGROOT").get("RESULT").get("DOCSET").get("DOC");
		primoNMBibNode = docNode.get("PrimoNMBib");
		getItNode = docNode.get("GETIT");

		Citation citation = citationService.addCitation(getType().toLowerCase());

		setCitationProperty(citation, primoNMBibNode, "year", new ArrayList<String>(){{  add("record"); add("addata"); add("date");}} );
		setCitationProperty(citation, primoNMBibNode, "publisher", new ArrayList<String>(){{  add("record"); add("addata"); add("pub");}} );
		setCitationProperty(citation, primoNMBibNode, "publicationLocation", new ArrayList<String>(){{  add("record"); add("addata"); add("cop");}} );
		setCitationProperty(citation, primoNMBibNode, "edition", new ArrayList<String>(){{  add("record"); add("display"); add("edition");}} );
		setCitationProperty(citation, primoNMBibNode, "doi", new ArrayList<String>(){{ add("record"); add("addata"); add("doi"); }} );
		setCitationProperty(citation, getItNode, "otherIds", new ArrayList<String>(){{  add("@GetIt2"); }} );

		return citation;

	}
}
