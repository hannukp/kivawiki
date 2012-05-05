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


import javax.servlet.http.HttpServletRequest;

import org.kivawiki.site.WikiService;
import org.kivawiki.site.WikiUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/search")
@Transactional
public class SearchController {
	@Autowired WikiService wiki;
	
	@RequestMapping(method=RequestMethod.GET)
	public Object get(
			HttpServletRequest req,
			@RequestParam(value="proj") String proj,
			@RequestParam(value="q") String query) {
		if (!wiki.auth(req, proj)) {
			return wiki.authFail(req, proj);
		}
		
		ModelAndView mav = new ModelAndView("search");
		mav.addObject("navigation_items", wiki.getNavigation(proj, "/"));
		mav.addObject("query", query);
		mav.addObject("proj", proj);
		mav.addObject("results", wiki.search(proj, query));
		mav.addObject("username", WikiUtils.getUsername(req));
		return mav;
	}
}
