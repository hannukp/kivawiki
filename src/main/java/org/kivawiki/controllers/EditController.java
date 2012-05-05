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


import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kivadoc.SectionExtentExtractor.SectionExtents;
import org.kivadoc.Uri;
import org.kivadoc.utils.UtfUtils;
import org.kivawiki.repo.RepoEditConflict;
import org.kivawiki.repo.RepoUser;
import org.kivawiki.repo.RepositoryException;
import org.kivawiki.site.DraftService;
import org.kivawiki.site.WikiService;
import org.kivawiki.site.WikiService.HtmlResult;
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
 * Controller for showing the editing form and
 * for saving the contents submitted by the form.
 */
@Controller
@RequestMapping("/edit")
@Transactional
public class EditController {
	private final Log log = LogFactory.getLog(EditController.class);
	
	@Autowired WikiService wiki;
	@Autowired DraftService draft;

	@RequestMapping(value="/{proj}/**", method=RequestMethod.GET)
	public Object get(
			HttpServletRequest req,
			@PathVariable String proj,
			@RequestParam(value="section", required=false) Integer section) {
		if (!wiki.auth(req, proj)) {
			return wiki.authFail(req, proj);
		}
		String uri = WikiUtils.getPathAfterPrefix("/edit/" + proj, req);
		
		String error = (String) req.getAttribute("kivawiki.error");
		HtmlResult preview = (HtmlResult) req.getAttribute("kivawiki.preview");
		String originalDoc = (String) req.getAttribute("kivawiki.originalDoc");
		String docFragment = (String) req.getAttribute("kivawiki.docFragment");
		String commitMessage = (String) req.getAttribute("kivawiki.commitMessage");
		Integer rev = (Integer) req.getAttribute("kivawiki.rev");
		
		if (docFragment == null || rev == null || originalDoc == null) {
			wiki.invalidateCache(proj, uri);
			byte[] bytes = wiki.getFile(proj, uri);
			String docText = "";
			if (bytes == null) {
				originalDoc = Base64.encodeBase64String(new byte[0]);
				docText = "";
				rev = -1;
			} else {
				originalDoc = Base64.encodeBase64String(bytes);
				docText = UtfUtils.decode(bytes);
				rev = wiki.getInfo(proj, uri).rev;
			}
			if (section != null) {
				SectionExtents extents = wiki.getSectionExtents(docText, section);
				docFragment = extractFragment(docText, extents.sectionStartLine, extents.sectionEndLine);
			} else {
				docFragment = docText;
			}
		}
	    if (commitMessage == null) {
	        if (rev == -1) {
	            commitMessage = "Added documentation file " + uri + ".";
	        } else {
	            commitMessage = "Updated documentation file " + uri + ".";
	        }
	    }
		
	    ModelAndView mav = new ModelAndView("edit_document");
	    mav.addObject("navigation_items", wiki.getNavigation(proj, uri));
	    mav.addObject("docFragment", docFragment);
	    mav.addObject("originalDoc", originalDoc);
	    mav.addObject("proj", proj);
	    mav.addObject("uri", uri);
	    mav.addObject("uri_html", wiki.htmlUri(uri));
	    mav.addObject("uri_parent", Uri.dirOf(uri));
	    mav.addObject("rev", rev);
	    mav.addObject("error", error);
	    if (preview != null) {
	    	mav.addObject("preview", preview.html);
	    	mav.addObject("preview_errors", preview.errors.isEmpty() ? null : preview.errors);
	    }
	    mav.addObject("section", section);
	    mav.addObject("commitMessage", commitMessage);
	    mav.addObject("username", WikiUtils.getUsername(req));
	    return mav;
	}

	@RequestMapping(value="/{proj}/**", method=RequestMethod.POST)
	public Object post(
			HttpServletRequest req,
			@PathVariable String proj,
			@RequestParam(value="preview", required=false) String preview,
			@RequestParam(value="edit") String docFragment,
			@RequestParam(value="commitMessage") String commitMessage,
			@RequestParam(value="originalDoc") String originalDoc,
			@RequestParam(value="rev") int rev,
			@RequestParam(value="section", required=false) Integer section) throws UnsupportedEncodingException {

		docFragment = docFragment.replace("\r", "");
		String uri = WikiUtils.getPathAfterPrefix("/edit/" + proj, req);
		
		req.setAttribute("kivawiki.originalDoc", originalDoc);
		req.setAttribute("kivawiki.docFragment", docFragment);
		req.setAttribute("kivawiki.rev", rev);
		req.setAttribute("kivawiki.commitMessage", commitMessage);
		
		// authenticate user before saving a draft copy
		RepoUser user = WikiUtils.getUser(req);
		if (user == null || !wiki.auth(req, proj)) {
			req.setAttribute("kivawiki.error", "You are not logged in!");
			return get(req, proj, section);
		}
		
		try {
			draft.saveDraft(user.getUsername(), proj, uri, docFragment);
		} catch (RuntimeException e) {
			// draft save may fail if DB is down; we would still want to
			// try the real save
			log.error("Draft save failed for " + user.getUsername() + ", " + proj + ", " + uri, e);
		}
		
		if (preview != null) {
			if (section == null) {
				req.setAttribute("kivawiki.preview", wiki.docToHtml(req.getContextPath(), proj, uri, docFragment, WikiService.HtmlType.FINAL));
			} else {
				req.setAttribute("kivawiki.preview", wiki.docFragmentToHtml(req.getContextPath(), proj, uri, docFragment));
			}
			return get(req, proj, section);
		}
		
		//docFragment is just the fragment being edited. Merge it with originalDoc here.
		String finalDoc;
		if (section == null) {
			finalDoc = docFragment;
		} else {
			String originalDocText = UtfUtils.decode(Base64.decodeBase64(originalDoc));
			SectionExtents extents = wiki.getSectionExtents(originalDocText, section);
			String mergeDocFragment;
			if (!docFragment.endsWith("\n")) {
				mergeDocFragment = docFragment + '\n';
			} else {
				mergeDocFragment = docFragment;
			}
			finalDoc = mergeDocuments(originalDocText, mergeDocFragment, extents.sectionStartLine, extents.sectionEndLine);
		}
		
		try {
			wiki.commit(proj, uri, user, rev, UtfUtils.encode(finalDoc), commitMessage);
		} catch (RepoEditConflict e) {
			wiki.invalidateCache(proj, uri);
			req.setAttribute("kivawiki.rev", wiki.getInfo(proj, uri).rev);
			req.setAttribute("kivawiki.docFragment", UtfUtils.decode(e.getConflictBytes()));
			req.setAttribute("kivawiki.error", "Edit conflict! Someone updated the same parts of the document that you did.");
	        return get(req, proj, null);
		} catch (RepositoryException e) {
			req.setAttribute("kivawiki.error", "Unknown svn error: " + e.getMessage());
	        return get(req, proj, section);
		}
		wiki.invalidateCacheFull(proj, uri);
		return "redirect:/view/" + URLEncoder.encode(proj + wiki.htmlUri(uri), "UTF-8").replace("%2F", "/");
		//return "redirect:/view/" + Utils.encodeUri(proj + wiki.htmlUri(uri), null, null);
	}

	static String mergeDocuments(String docText, String docFragment, Integer lineStart, Integer lineEnd) {
		String[] lines = splitLines(docText);
		int lineStart2 = normalizedStart(lineStart, lines.length);
		int lineEnd2 = normalizedEnd(lineEnd, lines.length);
		List<String> result = new ArrayList<String>();
		result.addAll(lineRange(lines, 0, lineStart2));
		result.add(docFragment);
		result.addAll(lineRange(lines, lineEnd2, lines.length));
		return joinLines(result);
	}

	static String extractFragment(String docText, Integer lineStart, Integer lineEnd) {
		String[] lines = splitLines(docText);
		int lineStart2 = normalizedStart(lineStart, lines.length);
		int lineEnd2 = normalizedEnd(lineEnd, lines.length);
		return joinLines(lineRange(lines, lineStart2, lineEnd2));
	}
	
	private static List<String> lineRange(String[] lines, int start, int end) {
		return Arrays.asList(Arrays.copyOfRange(lines, start, end));
	}

	private static String joinLines(List<String> result) {
		return StringUtils.join(result, '\n');
	}
	
	private static String[] splitLines(String docText) {
		return StringUtils.splitPreserveAllTokens(docText, '\n');
	}

	private static int normalizedEnd(Integer lineEnd, int length) {
		return (lineEnd == null ? length : Math.max(0, Math.min(lineEnd - 1, length)));
	}

	private static int normalizedStart(Integer lineStart, int length) {
		return (lineStart == null ? 0 : Math.max(0, Math.min(lineStart - 1, length)));
	}
}
