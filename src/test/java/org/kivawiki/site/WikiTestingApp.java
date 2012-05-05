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
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Properties;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.tools.ToolContext;
import org.apache.velocity.tools.ToolManager;
import org.junit.Ignore;
import org.kivawiki.repo.FileInfo;
import org.kivawiki.repo.LogInfo;
import org.kivawiki.repo.SvnUtils;
import org.kivawiki.site.DraftService.DraftResult;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * For running manual tests.
 */
@Ignore
public class WikiTestingApp {
	public static void main(String[] args) throws Exception {
		//velocity1();
		//velocity2();
		//misc();
		//svn();
		//svnSync();
		//System.out.println(new URL("http://subclipse.tigris.org/source/browse/*checkout*/subclipse/trunk/svnClientAdapter/changelog.txt?revision=4815").getContent());
		//draftTest();
		//cacheTest();
		//svnInternalCachingTest();
	}

	static void draftTest() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("/org/kivawiki/applicationContext.xml");
		DraftService draftService = context.getBean(DraftService.class);
		
		draftService.saveDraft("hvk2", "roads", "/foo/bar.txt", "test for hvk2roadsb");
		
		for (DraftResult dr : draftService.getDrafts("hvk2", "roads")) {
			System.out.println(dr);
			System.out.println(draftService.getDraft(dr.username, dr.proj, dr.uri, dr.date));
			System.out.println("----------------");
		}
		
		context.destroy();
	}

	static void cacheTest() throws Exception {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("/org/kivawiki/applicationContext-testing.xml");
		WikiService service = context.getBean(WikiService.class);
		for (int i = 0; i < 100; i++) {
			System.out.println(service.doesResourceExist("dfd", "/home.txt"));
			Thread.sleep(5000L);
		}
		context.destroy();
	}
	
	static void svnSync() throws Exception {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("/org/kivawiki/applicationContext.xml");
		Thread.sleep(1000L);
		/*
		WikiService wikiService = context.getBean(WikiService.class);
		long t0 = System.currentTimeMillis();
		for (int i = 0; i < 100; i++) {
			wikiService.getRepo("roads");
		}
		long t1 = System.currentTimeMillis();
		System.out.println("Elapsed:" + (t1 - t0));
		*/
		Cache sitemapCache = context.getBean(CacheManager.class).getCache("sitemap");
		SitemapEntry sitemap = CacheUtils.getValue(sitemapCache, "roads");
		printSiteMapRecur(0, sitemap);
		context.destroy();
	}
	
	static void printSiteMapRecur(int indent, SitemapEntry se) {
		System.out.println(StringUtils.repeat(" ", indent * 2) + "- '" + se.uri + "', '" + se.title + "'");
		if (se.children != null) {
			for (SitemapEntry c : se.children) {
				printSiteMapRecur(indent + 1, c);
			}
		}
	}

	static void svn() throws Exception {
		//System.out.println(info(null, "file:///C:/omat/python/django_test/docrepo1", "/broken2.txt"));
		
		String testRepo = "file:///C:/omat/python/_big/kivawiki-python/docrepo1";
		
		System.out.println(WikiService.fetchProjectMap("file:///C:/omat/java/kivawiki_workdir/projects.txt"));
		System.out.println(WikiService.fetchProjectMap("svn:" + testRepo + "/projects.txt"));

		System.out.println(new String(SvnUtils.cat(null, testRepo, "/broken.txt")));
		System.out.println(new String(SvnUtils.cat(null, testRepo + "/projects.txt", -1)));
		
		System.out.println();
		System.out.println("LIST:");
		for (FileInfo i : SvnUtils.ls(null, testRepo, "/")) {
			System.out.println(i);
		}
		System.out.println();
		System.out.println("LOG:");
		for (LogInfo i : SvnUtils.log(null, testRepo, "/", 5, "HEAD")) {
			System.out.println(i);
		}
		System.out.println();
		System.out.println(SvnUtils.diff(null, testRepo, "/", 63));
		System.out.println();
		//commit(new RepoUser("hvktest", "123"), "file:///C:/omat/python/django_test/docrepo1", "/broken.txt", 63, "BOOYA".getBytes("UTF-8"), "helo2");
		
		//System.out.println(info());
		System.out.println(SvnUtils.info(null, testRepo, "/brok.txt"));
	}

	static void misc() {
		try {
			String url = new URI(null, null, "/foo bar/bazå", null, "frag").toString();
			System.out.println(URLEncoder.encode("/foo bar/baz中.doc?foo=bar&helo#yes", "UTF-8"));
			System.out.println(StringEscapeUtils.escapeXml(URLEncoder.encode("/foo bar/baz中.doc?foo=bar&helo#yes", "UTF-8")).replace("%2F", "/"));
			
			System.out.println(url);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		System.exit(1);
		
		//System.out.println(new FileInfo().get("rev"));
		ApplicationContext context = new ClassPathXmlApplicationContext(
			        new String[] {"/WEB-INF/applicationContext.xml"});
		
		final CacheManager cm = context.getBean(CacheManager.class);
		
		/*
		PlatformTransactionManager tx = context.getBean(PlatformTransactionManager.class);
		String execute = new TransactionTemplate(tx).execute(new TransactionCallback<String>() {
			@Override
			public String doInTransaction(TransactionStatus arg0) {
				return null;
			}
		});
		*/
		Cache cache = cm.getCache("html");
		System.out.println(cache);
		
		cache.put(new Element("key2", "value3", true, 10, 200));
		System.out.println(cache.getMemoryStoreSize());
		System.out.println(cache.getDiskStoreSize());
		cache.flush();
		System.out.println(cache.getMemoryStoreSize());
		System.out.println(cache.getDiskStoreSize());
		System.out.println(cache.get("key2"));
		cm.shutdown();
		//[ name = kivawiki status = STATUS_ALIVE eternal = false overflowToDisk = false maxElementsInMemory = 0 maxElementsOnDisk = 0 memoryStoreEvictionPolicy = LRU timeToLiveSeconds = 0 timeToIdleSeconds = 0 diskPersistent = false diskExpiryThreadIntervalSeconds = 120 cacheEventListeners: net.sf.ehcache.statistics.LiveCacheStatisticsWrapper  hitCount = 0 memoryStoreHitCount = 0 diskStoreHitCount = 0 missCountNotFound = 0 missCountExpired = 0 overflowToOffHeap = false maxMemoryOffHeap = null ]
		//[ name = kivawiki status = STATUS_ALIVE eternal = false overflowToDisk = true maxElementsInMemory = 1000 maxElementsOnDisk = 10000 memoryStoreEvictionPolicy = LRU timeToLiveSeconds = 3600 timeToIdleSeconds = 600 diskPersistent = true diskExpiryThreadIntervalSeconds = 120 cacheEventListeners: net.sf.ehcache.statistics.LiveCacheStatisticsWrapper  hitCount = 0 memoryStoreHitCount = 0 diskStoreHitCount = 0 missCountNotFound = 0 missCountExpired = 0 overflowToOffHeap = false maxMemoryOffHeap = null ]
	}
	
	static void velocity2() {
		ToolManager manager = new ToolManager();
		manager.configure("src/main/webapp/WEB-INF/toolbox.xml");
		ToolContext context = manager.createContext();
	
		Properties p = new Properties();
	    p.setProperty("file.resource.loader.path", "src/main/webapp/WEB-INF/views");
	    VelocityEngine ve = new VelocityEngine();
		ve.init(p);
		Template t = ve.getTemplate("welcome.vm");
		//#macro(xenc $sometext)$tools.escapeEntities($sometext)#end
		//http://velocity.apache.org/engine/releases/velocity-1.7/developer-guide.html#Velocity_and_XML
		
		context.put("name", "Velocity");
		context.put("info", new FileInfo());
		context.put("info2", "helo");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		System.out.println(writer.toString());
	}

	static void velocity1() {
		Properties p = new Properties();
	    p.setProperty("file.resource.loader.path", "src/main/webapp/WEB-INF/views");
	    VelocityEngine ve = new VelocityEngine();
		ve.init(p);
		Template t = ve.getTemplate("welcome.vm");
		//#macro(xenc $sometext)$tools.escapeEntities($sometext)#end
		//http://velocity.apache.org/engine/releases/velocity-1.7/developer-guide.html#Velocity_and_XML
		
		VelocityContext context = new VelocityContext();
		context.put("name", "Velocity");
		StringWriter writer = new StringWriter();
		t.merge(context, writer);
		System.out.println(writer.toString());
	}

	static void svnInternalCachingTest() throws IOException {
		File testingRepo = new File("C:/omat/java/kivawiki_workdir/testing-repo");
		File testingRepoOrig = new File("C:/omat/java/kivawiki_workdir/testing-repo-orig");
		
		FileUtils.deleteDirectory(testingRepo);
		FileUtils.copyDirectory(testingRepoOrig, testingRepo);
		
		String repo = "file:///C:/omat/java/kivawiki_workdir/testing-repo";
		System.out.println(new String(SvnUtils.cat(null, repo, "/home.txt")));

		SvnUtils.commit(null, repo, "/home.txt", 1, "yessire".getBytes(), "new entry");
		//System.out.println(new String(SvnUtils.cat(null, repo, "/home.txt")));
		
		FileUtils.deleteDirectory(testingRepo);
		FileUtils.copyDirectory(testingRepoOrig, testingRepo);
		
		System.out.println(new String(SvnUtils.cat(null, repo, "/home.txt")));
	}
}
