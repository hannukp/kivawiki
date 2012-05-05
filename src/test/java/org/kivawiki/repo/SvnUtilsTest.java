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
package org.kivawiki.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kivawiki.repo.FileInfo;
import org.kivawiki.repo.LogInfo;
import org.kivawiki.repo.RepoEditConflict;
import org.kivawiki.repo.RepoFileNotFound;
import org.kivawiki.repo.SvnUtils;
import org.tigris.subversion.svnclientadapter.ISVNClientAdapter;
import org.tigris.subversion.svnclientadapter.SVNClientException;

public class SvnUtilsTest {
	private ISVNClientAdapter client = SvnUtils.getClient(null);
	private File repoDir;
	private String repoUrl;

	@Before
	public void createRepo() throws SVNClientException, IOException {
		repoDir = createTempDirectory();
		repoUrl = repoDir.toURI().toURL().toString().replace("file:/", "file:///");
		client.createRepository(repoDir, null);
	}
	
	@After
	public void removeRepo() {
		repoDir.delete();
	}

	@Test
	public void basic_repository_use() throws SVNClientException, MalformedURLException, UnsupportedEncodingException {
		assertTrue(SvnUtils.ls(null, repoUrl, "/").isEmpty());
		
		// add a new file
		SvnUtils.commit(null, repoUrl, "/foo.txt", -1, "hello".getBytes("UTF-8"), "test commit");
		
		// verify the file is valid
		List<FileInfo> ls = SvnUtils.ls(null, repoUrl, "/");
		assertEquals(1, ls.size());
		assertEquals(true, ls.get(0).isFile);
		assertEquals(1, ls.get(0).rev);
		assertEquals("/foo.txt", ls.get(0).uri);
		assertEquals(5, ls.get(0).size);
		
		// modify the file
		SvnUtils.commit(null, repoUrl, "/foo.txt", 1, "hello world".getBytes("UTF-8"), "test change");
		
		// verify the file is valid
		ls = SvnUtils.ls(null, repoUrl, "/");
		assertEquals(1, ls.size());
		assertEquals(true, ls.get(0).isFile);
		assertEquals(2, ls.get(0).rev);
		assertEquals("/foo.txt", ls.get(0).uri);
		assertEquals(11, ls.get(0).size);
		
		// make a conflicting edit (different change based on same revision, rev #1)
		try {
			SvnUtils.commit(null, repoUrl, "/foo.txt", 1, "hello worldy".getBytes("UTF-8"), "test conflict");
			fail();
		} catch (RepoEditConflict e) {
			// verify the conflict message is valid
			String conflictText = new String(e.getConflictBytes(), "UTF-8");
			assertEquals(
					"<<<<<<< .mine\nhello worldy=======\nhello world>>>>>>> .r2",
					conflictText.trim().replaceAll("\r", ""));
		}
		
		// test "cat" for different revisions
		assertEquals("hello", new String(SvnUtils.cat(null, repoUrl + "foo.txt", 1), "UTF-8"));
		assertEquals("hello world", new String(SvnUtils.cat(null, repoUrl + "foo.txt", 2), "UTF-8"));
		assertEquals("hello world", new String(SvnUtils.cat(null, repoUrl, "/foo.txt"), "UTF-8"));
		
		List<LogInfo> log = SvnUtils.log(null, repoUrl, "/", 5, "HEAD");
		assertEquals(2, log.size());
		
		// log entries are in reverse chronological order
		assertEquals(2, log.get(0).rev);
		assertEquals("test change", log.get(0).message);
		
		assertEquals(1, log.get(1).rev);
		assertEquals("test commit", log.get(1).message);
		
		log = SvnUtils.log(null, repoUrl, "/", 1, "HEAD");
		assertEquals(1, log.size());
		
		assertEquals(2, log.get(0).rev);
		assertEquals("test change", log.get(0).message);
	}

	@Test
	public void illegal_urls_should_fail() {
		// test "cat" for invalid URL
		try {
			SvnUtils.cat(null, repoUrl, "/\nfoo::@.txt2");
			fail("invalid URL should fail");
		} catch (RepoFileNotFound e) {
		}
		
		// test "cat" for illegal access URL
		try {
			SvnUtils.cat(null, repoUrl, "/../foo.txt");
			fail("illegal access URL should fail");
		} catch (IllegalArgumentException e) {
		}
		
		// test "cat" for illegal access URL
		try {
			SvnUtils.cat(null, repoUrl, "foo.txt");
			fail("illegal access URL should fail");
		} catch (IllegalArgumentException e) {
		}
		
		// test "cat" for non-existing file
		try {
			SvnUtils.cat(null, repoUrl, "/foo.txt2");
			fail("non-existing file should fail");
		} catch (RepoFileNotFound e) {
		}
	}
	
	public static File createTempDirectory() throws IOException {
		File temp = File.createTempFile("temp", "dir");
		if (!temp.delete() || !temp.mkdir()) {
			throw new IOException("Could not create temp dir: " + temp.getAbsolutePath());
		}
		return temp;
	}
}
