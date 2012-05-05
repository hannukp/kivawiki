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

import org.kivawiki.repo.ProjectInfo;
import org.kivawiki.site.WikiService;
import org.kivawiki.site.WikiUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/")
@Transactional
public class ProjectListController {
	@Autowired WikiService wiki;
	
	@RequestMapping(value="/", method=RequestMethod.GET)
	public Object get(HttpServletRequest req) {
		List<ProjectInfo> projects = wiki.getProjects();
		ModelAndView mav = new ModelAndView("project_list");
		mav.addObject("projs", projects);
		mav.addObject("username", WikiUtils.getUsername(req));
		return mav;
	}
}
