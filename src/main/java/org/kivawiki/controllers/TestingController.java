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


import java.io.File;
import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.ehcache.CacheManager;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kivawiki.site.SvnSync;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * This controller enables functional testing using
 * for example Selenium. Basically you can invoke
 * "GET /testing/reset" to reset the wiki to a known
 * initial state at the beginning of each test.
 * <p>
 * It's disabled by default and will return "403 FORBIDDEN".
 * You'll have to set the 'testingMode' property to 'true'
 * to enable it.
 */
@Controller
@RequestMapping("/testing")
@Transactional
public class TestingController {
	private final Log log = LogFactory.getLog(TestingController.class);
	
	@Autowired SvnSync svnSync;
	
	private CacheManager cacheManager;
	private File workDir;
	private boolean testingMode;
	private File testingRepo;
	private File testingRepoOrig;

	@Autowired
	public void setCacheManager(CacheManager cm) {
		this.cacheManager = cm;
	}

	@Value("${workdir}")
	public void setWorkDir(File workDir) {
		this.workDir = workDir;
	}
	
	@Value("${testingMode}")
	public void setTestingMode(boolean testingMode) {
		this.testingMode = testingMode;
		if (testingMode) {
			log.warn("RUNNING IN TESTING MODE!");
		}
	}
	
	@Value("${testingRepo}")
	public void setTestingRepo(File testingRepo) {
		this.testingRepo = testingRepo;
	}
	
	@Value("${testingRepoOrig}")
	public void setTestingRepoOrig(File testingRepoOrig) {
		this.testingRepoOrig = testingRepoOrig;
	}
	

	@RequestMapping(value="/reset", method=RequestMethod.GET)
	public Object get(HttpServletRequest req, HttpServletResponse res) throws IOException {
		if (!testingMode) {
			res.setStatus(HttpStatus.FORBIDDEN.value());
			return null;
		}
		
		FileUtils.deleteDirectory(testingRepo);
		FileUtils.copyDirectory(testingRepoOrig, testingRepo);
		FileUtils.deleteDirectory(workDir);
		cacheManager.clearAll();
		
		svnSync.sync();
		byte[] bytes = "OK!".getBytes("UTF-8");
		res.setContentType("text/plain;charset=UTF-8");
		res.setContentLength(bytes.length);
		res.getOutputStream().write(bytes);
		return null;
	}
}
