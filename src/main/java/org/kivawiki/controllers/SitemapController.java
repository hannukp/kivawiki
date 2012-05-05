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
package org.kivawiki.controllers;


import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringEscapeUtils;
import org.kivawiki.repo.FileInfo;
import org.kivawiki.site.SitemapEntry;
import org.kivawiki.site.WikiService;
import org.kivawiki.site.WikiUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/sitemap")
@Transactional
public class SitemapController {
	@Autowired WikiService wiki;
	
	@RequestMapping(value="/{proj}", method=RequestMethod.GET)
	public Object get(HttpServletRequest req, @PathVariable String proj) {
		if (!wiki.auth(req, proj)) {
			return wiki.authFail(req, proj);
		}
		
		String error = null;
		
		SitemapEntry sitemap = wiki.getSitemap(proj);
		List<FileInfo> orphans = wiki.getOrphans(proj);
		String sitemapHtml = null;
		
		if (sitemap == null || orphans == null) {
			error = "Sitemap is not available.";
		} else {
			Set<String> orphanUris = new HashSet<String>();
			for (FileInfo f : orphans) {
				orphanUris.add(f.uri);
			}
			StringBuilder b = new StringBuilder();
			b.append("<ul>");
			buildSitemap(b, orphanUris, req.getContextPath() + "/view/" + proj, sitemap);
			b.append("</ul>");
			sitemapHtml = b.toString();
		}
		
		ModelAndView mav = new ModelAndView("sitemap");
	    mav.addObject("navigation_items", wiki.getNavigation(proj, "/"));
		mav.addObject("proj", proj);
		mav.addObject("sitemap_html", sitemapHtml);
		mav.addObject("orphans", orphans);
		mav.addObject("error", error);
		mav.addObject("username", WikiUtils.getUsername(req));
		return mav;
	}

	private void buildSitemap(StringBuilder b, Set<String> orphanUris, String contextPath, SitemapEntry node) {
		b.append("<li>");
		String url = WikiUtils.encodeUri(contextPath + (wiki.isDocumentFile(node.uri) ? wiki.htmlUri(node.uri) : node.uri), null, null);
		b.append("<a href=\"" + StringEscapeUtils.escapeXml(url) + "\">" + StringEscapeUtils.escapeXml(node.title) + "</a>");
		
		if (wiki.isDocumentFile(node.uri) && orphanUris.contains(node.uri)) {
			b.append(" (orphan)");
		}
		
		if (node.children != null && !node.children.isEmpty()) {
			b.append("<ul>");
			for (SitemapEntry e : node.children) {
				buildSitemap(b, orphanUris, contextPath, e);
			}
			b.append("</ul>");
		}
		
		b.append("</li>");
	}
}
