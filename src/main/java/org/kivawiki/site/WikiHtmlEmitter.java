/**
 * Copyright 2012 Hannu Kankaanpää
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 *
 * @author Hannu Kankaanpää <hannu.kp@gmail.com>
 */
package org.kivawiki.site;


import java.util.Set;

import org.kivadoc.Uri;
import org.kivadoc.dom.Header;
import org.kivadoc.emitters.DocHtmlEmitter;
import org.kivadoc.emitters.HtmlBuilder;

public class WikiHtmlEmitter extends DocHtmlEmitter {
	private final WikiService wiki;
	private final String viewContextPath;
	private final String editContextPath;
	private final String proj;
	
	private int sectionNumber = 0;

	public WikiHtmlEmitter(WikiService wiki, String viewContextPath, String editContextPath, String proj, String documentUri) {
		this.wiki = wiki;
		this.viewContextPath = viewContextPath;
		this.editContextPath = editContextPath == null ? null : editContextPath + documentUri;
		this.proj = proj;
		this.documentDirUri = Uri.dirName(documentUri);
	}
	
	@Override
	protected void header(Header e) {
		super.header(e);
		if (editContextPath != null) {
			String query = "section=" + sectionNumber;
			b.write("<div style=\"clear:both; height: 0;\"></div>");
			b.write("<p class=\"sectionedit\">[<a href=\"" + HtmlBuilder.textToHtml(WikiUtils.encodeUri(editContextPath, query, null)) + "\">edit</a>]</p>");
			sectionNumber++;
		}
	}
	
	@Override
	public String getTitle(DocTarget target) {
		String title = "";
		if (!target.isInternal()) {
			title = wiki.getTitle(proj, target.uri + ".html");
		}
		return makeTitleWithFragment(title, target.frag);
	}
	
	@Override
	protected boolean doesTargetExist(DocTarget target) {
		Set<String> targets = wiki.getAllTargets(proj, target.uri + ".html");
		return targets != null && (target.frag == null || targets.contains(target.frag));
	}
	
	@Override
	protected boolean doesResourceExist(String uri) {
		return wiki.doesResourceExist(proj, uri);
	}
	
	@Override
	public String getUrlPath(String uri) {
		return viewContextPath + uri;
	}
}
