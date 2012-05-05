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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.kivadoc.utils.UtfUtils;
import org.kivawiki.repo.RepoFileNotFound;
import org.kivawiki.site.WikiService;
import org.kivawiki.site.WikiUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for showing the diff between two document revisitions.
 */
@Controller
@RequestMapping("/diff")
@Transactional
public class DiffController {
	@Autowired WikiService wiki;
	
	@RequestMapping(value="/{proj}/**", method=RequestMethod.GET)
	public Object get(HttpServletRequest req, HttpServletResponse res, @PathVariable String proj, @RequestParam(value="rev") int rev) throws IOException {
		if (!wiki.auth(req, proj)) {
			return wiki.authFail(req, proj);
		}
		
		String uri = WikiUtils.getPathAfterPrefix("/diff/" + proj, req);
		String diff;
		try {
			diff = wiki.getDiff(proj, uri, rev);
		} catch (RepoFileNotFound e) {
			return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
		}
		byte[] bytes = UtfUtils.encode(diff);
		res.setContentType("text/plain;charset=UTF-8");
		res.setContentLength(bytes.length);
		res.getOutputStream().write(bytes);
		return null;
	}
}
