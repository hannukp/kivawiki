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


import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.kivadoc.Uri;
import org.kivawiki.repo.LogInfo;
import org.kivawiki.repo.RepoFileNotFound;
import org.kivawiki.site.WikiService;
import org.kivawiki.site.WikiUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for showing the log of a directory or a file.
 */
@Controller
@RequestMapping("/log")
@Transactional
public class LogController {
	@Autowired WikiService wiki;
	
	@RequestMapping(value="/{proj}/**", method=RequestMethod.GET)
	public Object get(HttpServletRequest req, @PathVariable String proj, @RequestParam(value="rev", defaultValue="HEAD") String rev) {
		if (!wiki.auth(req, proj)) {
			return wiki.authFail(req, proj);
		}
		
		String uri = WikiUtils.getPathAfterPrefix("/log/" + proj, req);
		String error = null;
		int limit = 10;
		List<LogInfo> log = null;
		try {
			log = wiki.getLog(proj, uri, limit + 1, rev);
		} catch (RepoFileNotFound e) {
			error = "File not found";
		}
		
		Integer moreStart = null;
		if (log != null && log.size() > limit) {
			moreStart = log.get(log.size() - 1).rev;
			log.remove(log.size() - 1);
		}
		
		ModelAndView mav = new ModelAndView("log");
		mav.addObject("navigation_items", wiki.getNavigation(proj, uri));
		mav.addObject("log", log);
		mav.addObject("proj", proj);
		mav.addObject("uri", uri);
		mav.addObject("uri_html", wiki.isDocumentFile(uri) ? wiki.htmlUri(uri) : null);
		mav.addObject("uri_parent", Uri.dirOf(uri));
		mav.addObject("is_file", !uri.endsWith("/"));
		mav.addObject("error", error);
		mav.addObject("more_start", moreStart);
		mav.addObject("username", WikiUtils.getUsername(req));
		return mav;
	}
}
