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


import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.kivawiki.repo.RepoUser;
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
@RequestMapping("/login")
@Transactional
public class LoginController {
	private static final int EXPIRY = 60*60*24*30; // 30 days
	@Autowired WikiService wiki;
	
	@RequestMapping(method=RequestMethod.GET)
	public Object get(
			HttpServletRequest req,
			@RequestParam(value="from", required=false) String comeFrom,
			@RequestParam(value="proj", required=false) String proj) {
		RepoUser user = WikiUtils.getUser(req);
		String error = null;
		if (user != null && comeFrom != null) {
			error = "User " + user.getUsername() + " is not authorized to view " + comeFrom;
		}
		ModelAndView mav = new ModelAndView("login");
		mav.addObject("proj", proj);
		mav.addObject("repo_url", proj != null ? wiki.getRepo(proj) : null);
		mav.addObject("from", comeFrom);
		mav.addObject("username", WikiUtils.getUsername(req));
		mav.addObject("error", error);
		return mav;
	}
	
	@RequestMapping(method=RequestMethod.POST)
	public Object login(
			HttpServletResponse res,
			@RequestParam(value="username") String username,
			@RequestParam(value="password") String password,
			@RequestParam(value="from", defaultValue="/") String comeFrom) {
		setCookie(res, "username", username);
		setCookie(res, "password", password);
		return "redirect:" + comeFrom;
	}

	@RequestMapping(value="/logout", method=RequestMethod.POST)
	public Object logout(
			HttpServletResponse res) {
		clearCookie(res, "username");
		clearCookie(res, "password");
		return "redirect:/";
	}

	private void clearCookie(HttpServletResponse res, String name) {
		Cookie cookie = new Cookie(name, "");
		cookie.setMaxAge(0);
		cookie.setPath("/");
		res.addCookie(cookie);
	}
	
	private void setCookie(HttpServletResponse res, String name, String value) {
		Cookie cookie = new Cookie(name, value);
		cookie.setMaxAge(EXPIRY);
		cookie.setPath("/");
		res.addCookie(cookie);
	}
}
