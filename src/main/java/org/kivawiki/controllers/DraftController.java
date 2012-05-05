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


import java.io.IOException;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.kivadoc.utils.UtfUtils;
import org.kivawiki.site.DraftService;
import org.kivawiki.site.DraftService.DraftResult;
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
 * Controller for draft-functionality.
 * <p>
 * You can list all your drafts, view a single draft,
 * or save a new draft.
 */
@Controller
@RequestMapping("/drafts")
@Transactional
public class DraftController {
	@Autowired WikiService wiki;
	@Autowired DraftService draft;
	
	@RequestMapping(value="/{proj}/", method=RequestMethod.GET)
	public Object listAll(HttpServletRequest req, @PathVariable String proj) {
		if (!wiki.auth(req, proj)) {
			return wiki.authFail(req, proj);
		}
		
		String username = WikiUtils.getUser(req).getUsername();
		
		List<DraftResult> drafts = draft.getDrafts(username, proj);
		
		ModelAndView mav = new ModelAndView("drafts");
	    mav.addObject("navigation_items", wiki.getNavigation(proj, "/"));
		mav.addObject("proj", proj);
		mav.addObject("drafts", drafts);
		mav.addObject("username", WikiUtils.getUsername(req));
		return mav;
	}
	
	@RequestMapping(value="/{proj}/get", method=RequestMethod.GET)
	public Object showDraft(
			HttpServletRequest req,
			HttpServletResponse res,
			@PathVariable String proj,
			@RequestParam(value="uri") String uri,
			@RequestParam(value="date") Long date) throws IOException {
		if (!wiki.auth(req, proj)) {
			return wiki.authFail(req, proj);
		}
		
		String username = WikiUtils.getUser(req).getUsername();
		
		String draftString = draft.getDraft(username, proj, uri, new Date(date));
		byte[] bytes = UtfUtils.encode(draftString);
		res.setStatus(200);
		res.setContentType("text/plain;charset=UTF-8");
		res.setContentLength(bytes.length);
		res.getOutputStream().write(bytes);
		return null;
	}
	
	@RequestMapping(value="/{proj}/save", method=RequestMethod.POST)
	public Object saveDraft(
			HttpServletRequest req,
			HttpServletResponse res,
			@PathVariable String proj,
			@RequestParam(value="uri") String uri,
			@RequestParam(value="data") String data) throws IOException {
		
		if (!wiki.auth(req, proj)) {
			return wiki.authFail(req, proj);
		}
		
		String username = WikiUtils.getUser(req).getUsername();
		draft.saveDraft(username, proj, uri, data);
		
		res.setStatus(200);
		return null;
	}
}
