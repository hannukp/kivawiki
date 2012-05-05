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


import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.kivadoc.Uri;
import org.kivawiki.repo.LogInfo.Path;
import org.tigris.subversion.svnclientadapter.ISVNClientAdapter;
import org.tigris.subversion.svnclientadapter.ISVNDirEntry;
import org.tigris.subversion.svnclientadapter.ISVNInfo;
import org.tigris.subversion.svnclientadapter.ISVNLogMessage;
import org.tigris.subversion.svnclientadapter.ISVNLogMessageChangePath;
import org.tigris.subversion.svnclientadapter.SVNClientAdapterFactory;
import org.tigris.subversion.svnclientadapter.SVNClientException;
import org.tigris.subversion.svnclientadapter.SVNNodeKind;
import org.tigris.subversion.svnclientadapter.SVNRevision;
import org.tigris.subversion.svnclientadapter.SVNUrl;
import org.tigris.subversion.svnclientadapter.javahl.JhlClientAdapterFactory;

public class SvnUtils {
	public static FileInfo info(RepoUser user, String repo, String uri) {
		try {
			ISVNInfo info = getClient(user).getInfo(svnUrl(repo, uri));

			FileInfo i = new FileInfo();
			i.isFile = info.getNodeKind() == SVNNodeKind.FILE;
			i.rev = (int) info.getRevision().getNumber();
			i.author = info.getLastCommitAuthor();
			i.date = info.getLastChangedDate();
			i.url = info.getUrl().toString();
			i.root = info.getRepository().toString();
			return i;
		} catch (Exception e) {
			throw translateException(e);
		}
	}

	public static byte[] cat(RepoUser user, String repo, String uri) {
		return cat(user, repo, uri, -1);
	}

	public static byte[] cat(RepoUser user, String repo, String uri, int rev) {
		return cat(user, repoUrl(repo, uri), rev);
	}

	public static byte[] cat(RepoUser user, String repoUrl, int rev) {
		try {
			InputStream is = getClient(user).getContent(new SVNUrl(repoUrl), getRevision(rev));
			return IOUtils.toByteArray(is);
		} catch (Exception e) {
			throw translateException(e);
		}
	}

	public static List<FileInfo> ls(RepoUser user, String repo, String uri) {
		try {
			List<FileInfo> r = new ArrayList<FileInfo>();
			for (ISVNDirEntry e : getClient(user).getList(svnUrl(repo, uri), SVNRevision.HEAD, false)) {
				FileInfo i = new FileInfo();
				i.isFile = e.getNodeKind() == SVNNodeKind.FILE;
				i.uri = Uri.join(Uri.dirName(uri), e.getPath());
				i.size = e.getSize();
				i.rev = (int) e.getLastChangedRevision().getNumber();
				i.author = e.getLastCommitAuthor();
				i.date = e.getLastChangedDate();
				r.add(i);
			}
			return r;
		} catch (Exception e) {
			throw translateException(e);
		}
	}

	public static List<LogInfo> log(RepoUser user, String repo, String uri, int limit, String rev) {
		try {
			List<LogInfo> r = new ArrayList<LogInfo>();
			for (ISVNLogMessage m : getClient(user).getLogMessages(svnUrl(repo, uri), null, SVNRevision.getRevision(rev), new SVNRevision.Number(1), true, true, limit, true)) {
				List<LogInfo.Path> paths = new ArrayList<LogInfo.Path>();
				for (ISVNLogMessageChangePath path : m.getChangedPaths()) {
					Path p = new Path();
					p.action = path.getAction();
					p.uri = path.getPath();
					paths.add(p);
				}
				
				LogInfo i = new LogInfo();
				i.rev = (int) m.getRevision().getNumber();
				i.author = m.getAuthor();
				i.date = m.getDate();
				i.message = m.getMessage();
				i.paths = paths;
				r.add(i);
			}
			return r;
		} catch (Exception e) {
			throw translateException(e);
		}
	}
	
	public static String diff(RepoUser user, String repo, String uri, int rev) {
		try {
			File outFile = File.createTempFile("kivawiki-diff", "txt");
			try {
				getClient(user).diff(svnUrl(repo, uri), new SVNRevision.Number(rev - 1), new SVNRevision.Number(rev), outFile, true);
				return FileUtils.readFileToString(outFile, "UTF-8");
			} finally {
				FileUtils.deleteQuietly(outFile);
			}
		} catch (Exception e) {
			throw translateException(e);
		}
	}

	public static void commit(RepoUser user, String repo, String uri, int rev, byte[] data, String message) {
		File temp = null;
		try {
			ISVNClientAdapter client = getClient(user);
			uri = Uri.abs(uri);
			SVNUrl repoDir = svnUrl(repo, Uri.dirName(uri));
			String editFilename = Uri.baseName(uri);
			if (message == null) {
				if (rev == -1) {
					message = "Added documentation file " + editFilename + ".";
				} else {
					message = "Updated documentation file " + editFilename + ".";
				}
			}
			temp = File.createTempFile("kivawiki-checkout", "");
			if (!temp.delete() || !temp.mkdir()) {
				throw new RuntimeException("Could not create checkout directory");
			}
			client.checkout(repoDir, temp, SVNRevision.HEAD, 0, true, false);
			File repoFile = new File(temp, editFilename);
			if (rev > 0) {
				client.update(repoFile, new SVNRevision.Number(rev), true);
			}
			FileUtils.writeByteArrayToFile(repoFile, data);
			if (rev <= 0) {
				client.addFile(repoFile);
			}
			int retries = 2;
			while (retries >= 0) {
				try {
					client.commit(new File[] { temp }, message, true);
					break;
				} catch (SVNClientException e) {
					if (e.getMessage().contains("conflict in the working copy")) {
						throw new RepoEditConflict(FileUtils.readFileToByteArray(repoFile));
					}
					retries--;
					if (retries == 0) {
						throw e;
					}
					client.update(repoFile, SVNRevision.HEAD, true);
				}
			}
		} catch (Exception e) {
			throw translateException(e);
		} finally {
			FileUtils.deleteQuietly(temp);
		}
	}
	
	public static void checkoutOrUpdate(RepoUser user, String repo, File coDir) {
		try {
			ISVNClientAdapter client = getClient(user);
			if (!coDir.exists()) {
				client.checkout(new SVNUrl(repo), coDir, SVNRevision.HEAD, true);
			} else {
				client.update(coDir, SVNRevision.HEAD, true);
			}
		} catch (Exception e) {
			throw translateException(e);
		}
	}
	
	/**
	 * Return a list of files in the check-out-dir
	 */
	public static List<FileInfo> findFileList(RepoUser user, File coDir) {
		try {
			ISVNClientAdapter client = getClient(user);
			List<FileInfo> files = new ArrayList<FileInfo>();
			for (ISVNDirEntry e : client.getList(coDir, SVNRevision.HEAD, true)) {
				FileInfo i = new FileInfo();
				i.isFile = e.getNodeKind() == SVNNodeKind.FILE;
				i.uri = Uri.join("/", e.getPath());
				i.size = e.getSize();
				i.rev = (int) e.getLastChangedRevision().getNumber();
				i.author = e.getLastCommitAuthor();
				i.date = e.getLastChangedDate();
				files.add(i);
			}
			return files;
		} catch (Exception e) {
			throw translateException(e);
		}
	}
	

	private static volatile boolean setupDone = false;

	private static synchronized void setup() {
		if (!setupDone) {
			try {
				JhlClientAdapterFactory.setup();
			} catch (SVNClientException e) {
				throw new RuntimeException(JhlClientAdapterFactory.getLibraryLoadErrors(), e);
			}
			setupDone = true;
		}
	}

	static ISVNClientAdapter getClient(RepoUser user) {
		if (!setupDone) {
			setup();
		}
		try {
			ISVNClientAdapter client = SVNClientAdapterFactory.createSVNClient(SVNClientAdapterFactory.getPreferredSVNClientType());
			if (user != null) {
				client.setUsername(user.getUsername());
				client.setPassword(user.getPassword());
			}
			return client;
		} catch (SVNClientException e) {
			throw new RuntimeException(e);
		}
	}

	private static SVNRevision getRevision(int rev) {
		return rev < 0 ? SVNRevision.HEAD : new SVNRevision.Number(rev);
	}

	private static SVNUrl svnUrl(String repo, String uri) {
		try {
			return new SVNUrl(repoUrl(repo, uri));
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	public static String repoUrl(String repo, String uri) {
		return StringUtils.stripEnd(repo, "/") + "/" + StringUtils.stripStart(Uri.abs(uri), "/");
	}

	private static RuntimeException translateException(Exception e) {
		if (e instanceof RuntimeException) {
			return (RuntimeException) e;
		}
		if (e.getMessage().contains("svn: File not found")
				|| e.getMessage().contains("non-existent in rev")
				|| e.getMessage().contains("Filesystem has no item")
				|| e.getMessage().contains("path not found")
				|| e.getMessage().contains("Bogus URL")) {
			return new RepoFileNotFound();
		}
		return new RepositoryException(e);
	}

}
