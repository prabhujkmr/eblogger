/*
 *   This software is distributed under the terms of the FSF
 *   Gnu Lesser General Public License (see lgpl.txt).
 *
 *   This program is distributed WITHOUT ANY WARRANTY. See the
 *   GNU General Public License for more details.
 */
package com.scooterframework.tools.generator;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.scooterframework.tools.common.AbstractGenerator;
import com.scooterframework.tools.common.GeneratorHelper;

/**
 * This class updates resources.properties file.
 *
 * @author (Fei) John Chen
 */
public class ResourceGenerator extends AbstractGenerator {
	private String resource;

	public ResourceGenerator(Map<String, String> props, String resource) {
		super(props);

		this.resource = resource.toLowerCase();
	}

	@Override
	protected String getTemplateContent() {
		String fileName = getOutputFileName();
		String fullFileName = getRootPath() + File.separatorChar
				+ getRelativePathToOutputFile() + File.separatorChar + fileName;

		// add resource to "resources.list" property
		String marker = "@resources.list@";
		String property = "resources.list";
		List<String> contentLines = null;
		try {
			contentLines = GeneratorHelper
					.loadToStringListFromFile(fullFileName);
			int totalLines = contentLines.size();
			int markerLineIndex = getMarkerLineIndex(contentLines, marker);
			String nextLine = "";
			boolean nochange = false;
			if (markerLineIndex == -1) {
				throw new IllegalArgumentException(
						"There must be a line with marker \"" + marker
								+ "\" in file " + fileName + ".");
			} else if (markerLineIndex == totalLines - 1) {
				nextLine = "resources.list=" + resource;
				contentLines.add(nextLine);
			} else {
				int nextLineIndex = markerLineIndex + 1;
				nextLine = contentLines.get(nextLineIndex);
				nextLine = nextLine.trim();
				int propertyIndex = nextLine.indexOf(property);
				if (propertyIndex == -1) {
					nextLine = "resources.list=" + resource;
					log("resources.list add " + resource);
					contentLines.add(nextLineIndex, nextLine);
				}
				else {
					if (nextLine.startsWith("#")) {
						nextLine = "resources.list=" + resource;
					}
					else
					if (nextLine.indexOf(resource) == -1) {
						int equalSignIndex = nextLine.indexOf("=", propertyIndex);
						String currentResources = nextLine
								.substring(equalSignIndex + 1);
						nextLine = "resources.list=" + currentResources + ", "
								+ resource;
					}
					else {
						nochange = true;
						return null;
					}

					if (!nochange) {
						log("resources.list add " + resource);
						contentLines.remove(nextLineIndex);
						contentLines.add(nextLineIndex, nextLine);
					}
				}
			}
		} catch (Exception ex) {
			throw new IllegalArgumentException(ex.getMessage());
		}

		if (contentLines == null || contentLines.size() == 0)
			return "";

		StringBuilder tpl = new StringBuilder();
		Iterator<String> it = contentLines.iterator();
		while (it.hasNext()) {
			tpl.append(it.next()).append(linebreak);
		}
		return tpl.toString();
	}

	private int getMarkerLineIndex(List<String> lines, String marker) {
		int markerLineIndex = -1;
		int index = 0;
		for (String line : lines) {
			if (line.indexOf(marker) != -1) {
				markerLineIndex = index;
				break;
			}
			index++;
		}
		return markerLineIndex;
	}

	@Override
	protected Map<String, ?> getTemplateProperties() {
		return null;
	}

	@Override
	protected String getRelativePathToOutputFile() {
		return "config";
	}

	@Override
	protected String getOutputFileName() {
		return "routes.properties";
	}
}