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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.kivadoc.Uri;
import org.kivawiki.common.Struct;
import org.kivawiki.repo.FileInfo;
import org.kivawiki.site.WikiService;
import org.kivawiki.site.WikiService.HtmlResult;
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
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for showing one of the following:
 * <ul>
 * <li>A file index / file listing (url ends with "/")
 * <li>A document (url ends with ".html")
 * <li>A printable document (url ends with ".html" and has ?print=1)
 * <li>A file (otherwise)
 * </ul>
 */
@Controller
@RequestMapping("/view")
@Transactional
public class DocumentController {
	@Autowired WikiService wiki;
	
	@RequestMapping(value="/", method=RequestMethod.GET)
	public String rootView() {
		return "redirect:/";
	}
	
	@RequestMapping(value="/{proj}/**", method=RequestMethod.GET)
	public Object get(
			HttpServletRequest req,
			HttpServletResponse res,
			@PathVariable String proj,
			@RequestParam(value="rev", required=false) Integer rev,
			@RequestParam(value="print", required=false) Integer print)
	throws IOException {
		String uri = WikiUtils.getPathAfterPrefix("/view/" + proj, req);
	
		if (uri.endsWith("/")) {
			return showIndex(req, proj, uri);
		}
		
		if (uri.endsWith(".html")) {
			if (print != null && print != 0) {
				return showPrint(req, proj, uri);
			} else {
				return showDocument(req, proj, uri);
			}
		}
		
		return showFile(req, res, proj, uri, rev);
	}

	private Object showPrint(HttpServletRequest req, String proj, String uri) {
		if (!wiki.auth(req, proj)) {
			return wiki.authFail(req, proj);
		}
		HtmlResult htmlResult = wiki.getHtml(req.getContextPath(), proj, uri, WikiService.HtmlType.FINAL);
		if (htmlResult == null) {
			return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
		}
		FileInfo info = wiki.getInfo(proj, wiki.fileUri(uri));
		
		ModelAndView mav = new ModelAndView("plain_document");
		mav.addObject("title", wiki.getTitle(proj, uri));
		mav.addObject("data", htmlResult.html);
		mav.addObject("info", info);
		mav.addObject("extra_styles", htmlResult.extraStyles);
		return mav;
	}

	private Object showFile(HttpServletRequest req, HttpServletResponse res, String proj, String uri, Integer rev) throws IOException {
		if (!wiki.auth(req, proj)) {
			return new ResponseEntity<String>(HttpStatus.FORBIDDEN);
		}
		byte[] bytes;
		if (rev != null) {
			bytes = wiki.getFile(proj, uri, rev);
		} else {
			bytes = wiki.getFile(proj, uri);
		}
		if (bytes == null) {
			return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
		}
		res.setStatus(200);
		String uriL = uri.toLowerCase();
		if (uriL.endsWith(".txt")) {
			res.setContentType("text/plain;charset=UTF-8");
		} else if (uriL.endsWith(".png")) {
			res.setContentType("image/png");
		} else if (uriL.endsWith(".gif")) {
			res.setContentType("image/gif");
		} else if (uriL.endsWith(".jpg") || uriL.endsWith(".jpeg")) {
			res.setContentType("image/jpeg");
		} else if (uriL.endsWith(".css")) {
			res.setContentType("text/css");
		} else {
			res.setContentType("application/octet-stream");
		}
		WikiUtils.setCachingSeconds(res, 300);
		res.setContentLength(bytes.length);
		res.getOutputStream().write(bytes);
		return null;
	}

	private Object showDocument(HttpServletRequest req, String proj, String uri) {
		if (!wiki.auth(req, proj)) {
			return wiki.authFail(req, proj);
		}
		HtmlResult htmlResult = wiki.getHtml(req.getContextPath(), proj, uri, WikiService.HtmlType.EDITABLE);
		if (htmlResult == null) {
			return "redirect:/edit/" + proj + wiki.fileUri(uri);
		}
		FileInfo info = wiki.getInfo(proj, wiki.fileUri(uri));
		
		ModelAndView mav = new ModelAndView("document");
		mav.addObject("navigation_items", wiki.getNavigation(proj, uri));
		mav.addObject("title", wiki.getTitle(proj, uri));
		mav.addObject("data", htmlResult.html);
		mav.addObject("data_errors", htmlResult.errors.isEmpty() ? null : htmlResult.errors);
		mav.addObject("info", info);
		mav.addObject("proj", proj);
		mav.addObject("uri", uri);
		mav.addObject("uri_parent", Uri.dirOf(uri));
		mav.addObject("uri_file", wiki.fileUri(uri));
		mav.addObject("username", WikiUtils.getUsername(req));
		mav.addObject("extra_styles", htmlResult.extraStyles);
		return mav;
	}
	
	public static class IndexItem extends Struct {
		private static final long serialVersionUID = 1L;
		public String name;
		public String proj;
		public String uri;
		public String uri_html;
		public FileInfo info;
		public String title;

		public IndexItem(String name, String proj, String uri) {
			this.name = name;
			this.proj = proj;
			this.uri = uri;
		}
	}

	private Object showIndex(HttpServletRequest req, String proj, String uri) {
		if (!wiki.auth(req, proj)) {
			return wiki.authFail(req, proj);
		}
		
		List<IndexItem> files = new ArrayList<IndexItem>();
		files.add(new IndexItem(".", proj, uri));
		if (uri.equals("/")) {
			files.add(new IndexItem("..", null, null));
		} else {
			files.add(new IndexItem("..", proj, Uri.dirOf(uri)));
		}
		List<FileInfo> ls = wiki.getLs(proj, uri);
		Collections.sort(ls, new Comparator<FileInfo>() {
			@Override
			public int compare(FileInfo o1, FileInfo o2) {
				int c1 = getCategory(o1).compareTo(getCategory(o2));
				return c1 == 0 ? o1.uri.compareTo(o2.uri) : c1;
			}

			private Integer getCategory(FileInfo fi) {
				return !fi.isFile ? 0 : wiki.isDocumentFile(fi.uri) ? 1 : 2;
			}
		});
		for (FileInfo fi : ls) {
			String furi = fi.uri + (fi.isFile ? "" : "/");
			IndexItem ii = new IndexItem(Uri.baseName(fi.uri), proj, furi);
			ii.info = fi;
			if (wiki.isDocumentFile(fi.uri)) {
				ii.uri_html = wiki.htmlUri(fi.uri);
				ii.title = wiki.getTitle(proj, wiki.htmlUri(fi.uri));
			}
			files.add(ii);
		}
		
		ModelAndView mav = new ModelAndView("index");
		mav.addObject("navigation_items", wiki.getNavigation(proj, uri));
		mav.addObject("files", files);
		mav.addObject("proj", proj);
		mav.addObject("uri", uri);
		mav.addObject("username", WikiUtils.getUsername(req));
		mav.addObject("repo", StringUtils.stripEnd(wiki.getRepo(proj), "/"));
		return mav;
	}

}
