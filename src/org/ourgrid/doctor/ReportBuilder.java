package org.ourgrid.doctor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;

public class ReportBuilder {

	private Map<String, List<String>> variables = new HashMap<String, List<String>>();
	private String template;
	
	public ReportBuilder load(File templateFile) throws IOException {
		return load(IOUtils.toString(new FileInputStream(templateFile)));
	}
	
	public ReportBuilder load(String template) {
		this.template = template;
		return this;
	}
	
	public ReportBuilder set(String key, String value) {
		List<String> sections = new LinkedList<String>();
		sections.add(value);
		variables.put(key, sections);
		return this;
	}
	
	public ReportBuilder add(String key, String value) {
		List<String> sections = variables.get(key);
		if (sections == null) {
			sections = new LinkedList<String>();
			variables.put(key, sections);
		}
		sections.add(value);
		return this;
	}
	
	public String build() {
		String report = template;
		for (Entry<String, List<String>> variable : variables.entrySet()) {
			String key = variable.getKey();
			List<String> replacements = variable.getValue();
			StringBuilder replacementsBuilder = new StringBuilder();
			for (String replacement : replacements) {
				replacementsBuilder.append(replacement);
			}
			report = report.replaceAll("%" + key + "%", replacementsBuilder.toString());
		}
		return report;
	}
}
