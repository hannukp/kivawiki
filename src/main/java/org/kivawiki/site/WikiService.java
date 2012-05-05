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
package org.kivawiki.site;


import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.kivadoc.FullDocumentParser;
import org.kivadoc.KivaDocument;
import org.kivadoc.KivaError;
import org.kivadoc.SectionExtentExtractor;
import org.kivadoc.TargetExtractor;
import org.kivadoc.Uri;
import org.kivadoc.docparser.DocumentParser;
import org.kivadoc.dom.Body;
import org.kivadoc.emitters.FullHtmlEmitter;
import org.kivadoc.utils.UtfUtils;
import org.kivawiki.common.Struct;
import org.kivawiki.repo.FileInfo;
import org.kivawiki.repo.LogInfo;
import org.kivawiki.repo.ProjectInfo;
import org.kivawiki.repo.RepoFileNotFound;
import org.kivawiki.repo.RepoUser;
import org.kivawiki.repo.RepositoryException;
import org.kivawiki.repo.SvnUtils;
import org.kivawiki.site.CacheUtils.Loader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WikiService {
	private Cache htmlCache;
	private Cache fileCache;
	private Cache titleCache;
	private Cache infoCache;
	private Cache lsCache;
	private Cache authCache;
	private Cache searchCache;
	private Cache sitemapCache;
	private Cache orphanCache;
	private Cache repoCache;
	private Cache targetCache;
	private String projectFile;
	
	@Autowired
	public void setCacheManager(CacheManager cm) {
		this.htmlCache = cm.getCache("html");
		this.fileCache = cm.getCache("file");
		this.titleCache = cm.getCache("title");
		this.infoCache = cm.getCache("info");
		this.lsCache = cm.getCache("ls");
		this.authCache = cm.getCache("auth");
		this.searchCache = cm.getCache("search");
		this.sitemapCache = cm.getCache("sitemap");
		this.orphanCache = cm.getCache("orphan");
		this.repoCache = cm.getCache("repo");
		this.targetCache = cm.getCache("target");
	}
	
	@Value("${projects}")
	public void setProjectFile(String projectFile) {
		this.projectFile = projectFile;
	}
	
	public String getRepo(final String proj) {
		return CacheUtils.getCached(repoCache, CacheUtils.cacheName(proj), new Loader<String>() {
			@Override
			public String load() {
				ProjectInfo pi = fetchProjectMap().get(proj);
				if (pi == null) {
					throw new UnknownProjectException(proj);
				}
				return pi.repo;
			}
		});
	}
	
	public enum HtmlType { EDITABLE, FINAL }

	public HtmlResult getHtml(final String contextPath, final String proj, String uri, final HtmlType htmlType) {
		final String furi = fileUri(uri);
		
		return CacheUtils.getCached(htmlCache, CacheUtils.cacheName(proj, furi, htmlType), new Loader<HtmlResult>() {
			@Override
			public HtmlResult load() {
				byte[] bytes = getFile(proj, furi);
				if (bytes == null) {
					return null;
				}
				return docToHtml(contextPath, proj, furi, UtfUtils.decode(bytes), htmlType);
			}
		});
	}
	
	public static class HtmlResult implements Serializable {
		private static final long serialVersionUID = 1L;
		
		public String html;
		public String extraStyles = "";
		public final List<KivaError> errors = new ArrayList<KivaError>();
	}
	
	public HtmlResult docToHtml(String contextPath, String proj, String uri, String docText, HtmlType htmlType) {
		FullDocumentParser parser = new FullDocumentParser();
		KivaDocument doc = parser.parse(docText);
		FullHtmlEmitter emitter = new FullHtmlEmitter();
		String viewContextPath = contextPath + "/view/" + proj;
		String editContextPath = (htmlType == HtmlType.EDITABLE ? contextPath + "/edit/" + proj : null);
		emitter.setDocHtmlEmitter(new WikiHtmlEmitter(this, viewContextPath, editContextPath, proj, uri));
		HtmlResult result = new HtmlResult();
		result.html = emitter.toHtmlString(doc);
		result.errors.addAll(doc.getErrors());
		result.errors.addAll(emitter.getErrors());
		result.extraStyles = emitter.toExtraStyleHtmlString(doc);
		return result;
	}
	
	public HtmlResult docFragmentToHtml(String contextPath, String proj, String uri, String docText) {
		DocumentParser parser = new DocumentParser();
		Body body = new Body(parser.parse(docText));
		String viewContextPath = contextPath + "/view/" + proj;
		WikiHtmlEmitter emitter = new WikiHtmlEmitter(this, viewContextPath, null, proj, uri);
		emitter.setDocumentFragment(true);
		
		HtmlResult result = new HtmlResult();
		result.html = emitter.toHtmlString(body);
		result.errors.addAll(parser.getErrors());
		result.errors.addAll(emitter.getErrors());
		return result;
	}
	
	public SectionExtentExtractor.SectionExtents getSectionExtents(String docText, int sectionNumber) {
		FullDocumentParser parser = new FullDocumentParser();
		KivaDocument doc = parser.parse(docText);
		return SectionExtentExtractor.extract(doc.getBody(), sectionNumber);
	}
	
	public byte[] getFile(final String proj, final String uri) {
		try {
			return CacheUtils.getCached(fileCache, CacheUtils.cacheName(proj, uri), 60, new Loader<byte[]>() {
				@Override
				public byte[] load() {
					return SvnUtils.cat(null, getRepo(proj), uri);
				}
			});
		} catch (RepoFileNotFound e) {
			return null;
		}
	}

	public byte[] getFile(String proj, String uri, int rev) {
		try {
			return SvnUtils.cat(null, getRepo(proj), uri, rev);
		} catch (RepoFileNotFound e) {
			return null;
		}
	}

	/**
	 * Return file information. If the file does not exist, throws an exception.
	 * 
	 * @throws RepositoryException if the file can't be accessed.
	 * @throws RepoFileNotFound if the file does not exist.
	 * @throws UnknownProjectException if the project does not exist.
	 */
	public FileInfo getInfo(final String proj, final String uri) {
		return CacheUtils.getCached(infoCache, CacheUtils.cacheName(proj, uri), 60, new Loader<FileInfo>() {
			@Override
			public FileInfo load() {
				return SvnUtils.info(null, getRepo(proj), uri);
			}
		});
	}
	
	public List<FileInfo> getLs(final String proj, final String uri) {
		return CacheUtils.getCached(lsCache, CacheUtils.cacheName(proj, uri), new Loader<ArrayList<FileInfo>>() {
			@Override
			public ArrayList<FileInfo> load() {
				return (ArrayList<FileInfo>) SvnUtils.ls(null, getRepo(proj), uri);
			}
		});
	}
	
	public void commit(String proj, String uri, RepoUser user, int rev, byte[] data, String message) {
		SvnUtils.commit(user, getRepo(proj), uri, rev, data, message);
	}
	
	public List<LogInfo> getLog(String proj, String uri, int limit, String rev) {
		return SvnUtils.log(null, getRepo(proj), uri, limit, rev);
	}
	
	public String getTitle(final String proj, final String uri) {
		return CacheUtils.getCached(titleCache, CacheUtils.cacheName(proj, uri), new Loader<String>() {
			@Override
			public String load() {
				if (!uri.endsWith(".html")) {
					return uri;
				}
				byte[] bytes = getFile(proj, fileUri(uri));
				if (bytes == null) {
					return uri;
				}
				String title = new FullDocumentParser().parseTitle(UtfUtils.decode(bytes));
				if (StringUtils.isBlank(title)) {
					return uri;
				}
				return title;
			}
		});
	}
	
	public boolean doesResourceExist(final String proj, final String uri) {
		try {
			getInfo(proj, uri);
			return true;
		} catch (UnknownProjectException e) {
			return false;
		} catch (RepositoryException e) {
			return false;
		}
	}
	
	public Set<String> getAllTargets(final String proj, final String uri) {
		return CacheUtils.getCached(targetCache, CacheUtils.cacheName(proj, uri), new Loader<HashSet<String>>() {
			@Override
			public HashSet<String> load() {
				if (!uri.endsWith(".html")) {
					return null;
				}
				byte[] bytes = getFile(proj, fileUri(uri));
				if (bytes == null) {
					return null;
				}
				FullDocumentParser parser = new FullDocumentParser();
				KivaDocument doc = parser.parse(UtfUtils.decode(bytes));
				return (HashSet<String>) TargetExtractor.extract(doc.getBody());
			}
		});
	}
	
	public static class NavItem extends Struct {
		private static final long serialVersionUID = 1L;
		public String url;
		public String title;
		public NavItem(String url, String title) {
			this.url = url;
			this.title = title;
		}
	}

	/**
	 * Get list of items for the navigation bar.
	 */
	public List<NavItem> getNavigation(String proj, String uri) {
		if (!uri.endsWith("/")) {
			uri = Uri.dirOf(uri);
		}
		
		List<NavItem> res = new ArrayList<NavItem>();
		while (!uri.isEmpty()) {
			String u = uri + "home.html";
			String title0 = Uri.baseName(Uri.dirName(u));
			if (title0.isEmpty()) {
				title0 = proj;
			}
			byte[] home = getFile(proj, fileUri(u));
			String title;
			if (home == null) {
				// no home.html, link to folder
				u = uri;
				title = title0;
			} else {
				title = new FullDocumentParser().parseTitle(UtfUtils.decode(home));
				if (StringUtils.isBlank(title)) {
					title = title0;
				}
			}
			res.add(new NavItem("/view/" + proj + u, title));
			uri = Uri.dirOf(uri);
		}
		res.add(new NavItem("/sitemap/" + proj, "Sitemap"));
		Collections.reverse(res);
		return res;
	}
	
	public boolean isDocumentFile(String uri) {
		return uri.endsWith(".txt");
	}
	
	public String fileUri(String uri) {
		return Uri.splitExt(uri)[0] + ".txt";
	}
	
	public String htmlUri(String uri) {
		return Uri.splitExt(uri)[0] + ".html";
	}

	public List<ProjectInfo> getProjects() {
		ArrayList<ProjectInfo> result = new ArrayList<ProjectInfo>(fetchProjectMap().values());
		Collections.sort(result, new Comparator<ProjectInfo>() {
			@Override
			public int compare(ProjectInfo o1, ProjectInfo o2) {
				return o1.proj.compareTo(o2.proj);
			}
		});
		return result;
	}
	
	public Map<String, ProjectInfo> fetchProjectMap() {
		return fetchProjectMap(projectFile);
	}
	
	public static Map<String, ProjectInfo> fetchProjectMap(String url) {
		byte[] bytes;
		if (url.startsWith("svn:")) {
			bytes = SvnUtils.cat(null, url.substring(4), -1);
		} else {
			try {
				InputStream stream = new URL(url).openStream();
				bytes = IOUtils.toByteArray(stream);
				IOUtils.closeQuietly(stream);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		Map<String, ProjectInfo> result = new HashMap<String, ProjectInfo>();
		String text = UtfUtils.decode(bytes);
		for (String line : text.split("\r\n|\r|\n")) {
			if (StringUtils.isBlank(line)) {
				continue;
			}
			String[] split = line.split(" ", 3);
			ProjectInfo pi = new ProjectInfo();
			pi.repo = split[0];
			pi.proj = split[1];
			pi.title = split[2];
			result.put(pi.proj, pi);
		}
		return result;
	}

	public String getDiff(String proj, String uri, int rev) {
		return SvnUtils.diff(null, getRepo(proj), uri, rev);
	}
	
	public void invalidateCache(String proj, String uri) {
	    htmlCache.remove(CacheUtils.cacheName(proj, uri, HtmlType.EDITABLE));
	    htmlCache.remove(CacheUtils.cacheName(proj, uri, HtmlType.FINAL));
	    fileCache.remove(CacheUtils.cacheName(proj, uri));
	    titleCache.remove(CacheUtils.cacheName(proj, uri));
	    infoCache.remove(CacheUtils.cacheName(proj, uri));
	    targetCache.remove(CacheUtils.cacheName(proj, uri));
	}

	public void invalidateCacheFull(String proj, String uri) {
		invalidateCache(proj, uri);
		lsCache.remove(CacheUtils.cacheName(proj, Uri.dirOf(uri)));
	}
	
	/**
	 * Are we authorized to view the given project?
	 * @param req
	 * @param proj
	 * @return
	 */
	public boolean auth(HttpServletRequest req, String proj) {
		RepoUser user = WikiUtils.getUser(req);
		if (user == null) {
			return false;
		}
		
		String pw = DigestUtils.sha512Hex(user.getPassword());
		String ckey = CacheUtils.cacheName(user.getUsername(), pw, proj);
		Boolean authorized = CacheUtils.getValue(authCache, ckey);
		if (authorized == null || !authorized) {
			try {
				SvnUtils.info(user, getRepo(proj), "/");
			} catch (RepositoryException e) {
				return false;
			}
			CacheUtils.putResult(authCache, ckey, true);
		}
		return true;
	}

	/**
	 * What to display when authentication fails?
	 * @return
	 */
	public String authFail(HttpServletRequest req, String proj) {
		try {
			return "redirect:/login?from=" + URLEncoder.encode(req.getServletPath(), "UTF-8") + "&proj=" + URLEncoder.encode(proj, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	public List<SearchResult> search(String proj, String needle) {
		ArrayList<SearchEntry> searchIndex = CacheUtils.getValue(searchCache, proj);
		List<SearchResult> hits = new ArrayList<SearchResult>();
		
		String[] needles = needle.split("\\s+");
	    for (SearchEntry se : searchIndex) {
	    	boolean miss = false;
	    	String firstNeedle = null;
	        for (String n : needles) {
	        	n = n.toLowerCase();
	        	if (StringUtils.isBlank(n)) {
	        		continue;
	        	}
	        	if (firstNeedle == null) {
	        		firstNeedle = n;
	        	}
	        	if (!se.text.contains(n)) {
	        		miss = true;
	        		break;
	        	}
	        }
	        if (!miss && firstNeedle != null) {
	            int ix = se.text.indexOf(firstNeedle);
	            if (ix >= 0) {
	            	SearchResult sr = new SearchResult();
	            	sr.uri = htmlUri(se.uri);
	            	sr.title = getTitle(proj, sr.uri);
	            	sr.sample = StringUtils.substring(se.text, Math.max(0, ix - 50), ix + firstNeedle.length() + 50);
	            	hits.add(sr);
	            }
	        }
	    }
	    return hits;
	}

	public SitemapEntry getSitemap(String proj) {
		return CacheUtils.getValue(sitemapCache, proj);
	}
	
	public ArrayList<FileInfo> getOrphans(String proj) {
		return CacheUtils.getValue(orphanCache, proj);
	}
}
