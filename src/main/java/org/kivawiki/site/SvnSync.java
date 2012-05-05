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


import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kivadoc.FullDocumentParser;
import org.kivadoc.KivaDocument;
import org.kivadoc.TargetExtractor;
import org.kivadoc.Uri;
import org.kivadoc.dom.ElementVisitor;
import org.kivadoc.dom.TextRef;
import org.kivadoc.utils.UtfUtils;
import org.kivawiki.repo.FileInfo;
import org.kivawiki.repo.ProjectInfo;
import org.kivawiki.repo.SvnUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SvnSync {
	private final Log log = LogFactory.getLog(SvnSync.class);
	
	private Cache fileCache;
	private Cache titleCache;
	private Cache infoCache;
	private Cache lsCache;
	private Cache searchCache;
	private Cache orphanCache;
	private Cache sitemapCache;
	private Cache targetCache;
	
	private File workDir;
	private boolean testingMode;
	
	@Autowired WikiService wiki;

	
	@Autowired
	public void setCacheManager(CacheManager cm) {
		this.fileCache = cm.getCache("file");
		this.titleCache = cm.getCache("title");
		this.infoCache = cm.getCache("info");
		this.lsCache = cm.getCache("ls");
		this.searchCache = cm.getCache("search");
		this.orphanCache = cm.getCache("orphan");
		this.sitemapCache = cm.getCache("sitemap");
		this.targetCache = cm.getCache("target");
	}
	
	@Value("${workdir}")
	public void setWorkDir(File workDir) {
		this.workDir = workDir;
	}
	
	@Value("${testingMode}")
	public void setTestingMode(boolean testingMode) {
		this.testingMode = testingMode;
	}
	
	@Scheduled(fixedRate = 5 * 60 * 1000L)
	//@Scheduled(fixedDelay = 1000L)
	public void scheduledSync() throws IOException {
		if (!testingMode) {
			sync();
		}
	}
	
	public void sync() throws IOException {
		log.info("Syncing at " + workDir);
		for (ProjectInfo p : wiki.getProjects()) {
			log.info("Syncing: " + p.proj + ", " + p.repo);
			syncRepo(p.proj, p.repo);
		}
		log.info("Syncing done!");
	}
	
	private File repoWorkDir(String repo) {
		return new File(workDir, DigestUtils.sha512Hex(repo));
	}
	
	/**
	 * Return a map from directories to list of files/dirs in each directory.
	 */
	private Map<String, List<FileInfo>> buildDirMap(List<FileInfo> files) {
		Map<String, List<FileInfo>> dirs = new HashMap<String, List<FileInfo>>();
		dirs.put("/", new ArrayList<FileInfo>());
		for (FileInfo f : files) {
			String dn = f.uri + "/";
			if (!f.isFile && !dirs.containsKey(dn)) {
				dirs.put(dn, new ArrayList<FileInfo>());
			}
		}
		for (FileInfo f : files) {
			dirs.get(Uri.dirOf(f.uri)).add(f);
		}
		return dirs;
	}
	
	public void syncRepo(String proj, String repo) throws IOException {
		File coDir = repoWorkDir(repo);
		SvnUtils.checkoutOrUpdate(null, repo, coDir);
		
		List<FileInfo> files = SvnUtils.findFileList(null, coDir);
		Map<String, List<FileInfo>> dirs = buildDirMap(files);
		
		// ls cache
		for (Map.Entry<String, List<FileInfo>> e : dirs.entrySet()) {
			CacheUtils.putResult(lsCache, CacheUtils.cacheName(proj, e.getKey()), (Serializable) e.getValue());
		}
		
		// info cache
		for (FileInfo f : files) {
			CacheUtils.putResult(infoCache, CacheUtils.cacheName(proj, f.uri), f);
		}
		Map<String, String> titles = new HashMap<String, String>();
		Map<String, List<String>> links = new HashMap<String, List<String>>();
		ArrayList<SearchEntry> searchIndex = new ArrayList<SearchEntry>();
		
		// file cache, title cache, target cache, searchIndex
		for (FileInfo f : files) {
			if (!f.isFile) {
				continue;
			}
			
			File fn = new File(coDir, StringUtils.stripStart(f.uri, "/").replace("/", File.separator));
			byte[] bytes = FileUtils.readFileToByteArray(fn);
			CacheUtils.putResult(fileCache, CacheUtils.cacheName(proj, f.uri), bytes);
			
			if (wiki.isDocumentFile(f.uri)) {
				String text = UtfUtils.decode(bytes);
				KivaDocument doc = new FullDocumentParser().parse(text);
				CacheUtils.putResult(targetCache, CacheUtils.cacheName(proj, f.uri), (Serializable) TargetExtractor.extract(doc.getBody()));
				CacheUtils.putResult(titleCache, CacheUtils.cacheName(proj, f.uri), doc.getTitle());
				titles.put(f.uri, doc.getTitle());
				RefVisitor refVisitor = new RefVisitor(f.uri);
				refVisitor.visit(doc.getBody());
				links.put(f.uri, refVisitor.targets);
				
				SearchEntry se = new SearchEntry();
				se.uri = f.uri;
				se.text = text.toLowerCase();
				searchIndex.add(se);
			}
		}
		
		CacheUtils.putResult(searchCache, proj, searchIndex);
		CacheUtils.putResult(sitemapCache, proj, buildSiteMap(dirs, titles, proj));
		CacheUtils.putResult(orphanCache, proj, (Serializable) findOrphans(links, files));
	}
	
	private static class RefVisitor extends ElementVisitor {
		private final List<String> targets = new ArrayList<String>();
		private final String documentDirUri;
		
		public RefVisitor(String documentUri) {
			this.documentDirUri = Uri.dirName(documentUri);
		}
		
		@Override
		protected void textRef(TextRef e) {
			try {
				targets.add(Uri.abs(Uri.join(documentDirUri, e.getTargetUri() + ".txt")));
			} catch (IllegalArgumentException ex) {
				// just ignore invalid references
			}
		}
	}
	
	private List<FileInfo> findOrphans(Map<String, List<String>> links, List<FileInfo> files) {
		Set<String> visited = new HashSet<String>();
		findOrphansRecur(visited, links, "/home.txt");
		//System.out.println("VISITED: " + visited);
		//System.out.println("LINKS: " + links);
		
		List<FileInfo> ret = new ArrayList<FileInfo>();
		for (FileInfo f : files) {
			if (!visited.contains(f.uri) && f.isFile && wiki.isDocumentFile(f.uri)) {
				ret.add(f);
				//System.out.println("ORPHAN: " + f);
			}
		}
		return ret;
	}

	private void findOrphansRecur(Set<String> visited, Map<String, List<String>> links, String uri) {
		visited.add(uri);
		List<String> subs = links.get(uri);
		if (subs != null) {
			for (String sub : subs) {
				if (!visited.contains(sub)) {
					findOrphansRecur(visited, links, sub);
				}
			}
		}
	}

	private SitemapEntry buildSiteMap(Map<String, List<FileInfo>> dirs, Map<String, String> titles, String proj) {
		SitemapEntry se = buildSiteMapRecur(dirs, titles, proj, "/");
		//printSiteMapRecur(0, se);
		return se;
	}

	private SitemapEntry buildSiteMapRecur(Map<String, List<FileInfo>> dirs, Map<String, String> titles, String proj, String uri) {
		List<FileInfo> contents = dirs.get(uri);
		SitemapEntry ret = new SitemapEntry();
		for (FileInfo f : contents) {
			if (f.isFile && Uri.baseName(f.uri).equals("home.txt")) {
				ret.uri = f.uri;
				ret.title = titles.get(f.uri);
				ret.significant = true;
				break;
			}
		}
		if (ret.uri == null) {
			ret.uri = uri;
			ret.title = Uri.baseName(StringUtils.stripEnd(uri, "/"));
			if (ret.title.isEmpty()) {
				ret.title = proj;
			}
		}
		ret.children = new ArrayList<SitemapEntry>();
		for (FileInfo f : contents) {
			if (f.isFile) {
				continue;
			}
			SitemapEntry sub = buildSiteMapRecur(dirs, titles, proj, f.uri + "/");
			if (sub.significant) {
				ret.significant = true;
				ret.children.add(sub);
			}
		}
		for (FileInfo f : contents) {
			if (!f.isFile || !wiki.isDocumentFile(f.uri) || Uri.baseName(f.uri).equals("home.txt")) {
				continue;
			}
			SitemapEntry se = new SitemapEntry();
			se.uri = f.uri;
			se.title = titles.get(f.uri);
			ret.significant = true;
			ret.children.add(se);
		}
		Collections.sort(ret.children, new Comparator<SitemapEntry>() {
			@Override
			public int compare(SitemapEntry o1, SitemapEntry o2) {
				return o1.title.compareTo(o2.title);
			}
		});
		return ret;
	}
}
