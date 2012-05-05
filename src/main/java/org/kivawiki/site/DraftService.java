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


import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.kivadoc.utils.UtfUtils;
import org.kivawiki.common.Struct;
import org.kivawiki.common.StructRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DraftService {
	private SimpleJdbcTemplate jdbc;
	
	@Autowired
	public void init(DataSource dataSource) {
		this.jdbc = new SimpleJdbcTemplate(dataSource);
	}
	
	public static class DraftResult extends Struct {
		private static final long serialVersionUID = 1L;
		public String username;
		public String proj;
		public String uri;
		public Date date;
	}
	
	public void saveDraft(String username, String proj, String uri, String text) {
		try {
			byte[] bytes = jdbc.queryForObject(
					"select val from kivawiki.drafts " +
					"where username=? and proj=? and uri=? order by added desc limit 1",
					byte[].class, username, proj, uri);
		
			// do not save the same draft twice
			if (UtfUtils.decode(bytes).equals(text)) {
				return;
			}
		} catch (EmptyResultDataAccessException e) {
		}
		
		jdbc.update(
				"insert into kivawiki.drafts " +
				"(username, proj, uri, val, added) values (?, ?, ?, ?, ?)",
				username, proj, uri, UtfUtils.encode(text), new Date());
	}
	
	public void clearDrafts(String username, String proj) {
		jdbc.update(
				"delete from kivawiki.drafts " +
				"where username=? and proj=?",
				username, proj);
	}
	
	public List<DraftResult> getDrafts(String username, String proj) {
		return jdbc.query(
				"select username, proj, uri, added as \"date\" " +
				"from kivawiki.drafts " +
				"where username=? and proj=? " +
				"order by added desc limit 100",
				StructRowMapper.of(DraftResult.class), username, proj);
	}

	public String getDraft(String username, String proj, String uri, Date added) {
		try {
			byte[] bytes = jdbc.queryForObject(
					"select val from kivawiki.drafts " +
					"where username=? and proj=? and uri=? and added=? limit 1",
					byte[].class, username, proj, uri, added);
			return UtfUtils.decode(bytes);
		} catch (EmptyResultDataAccessException e) {
		}
		return null;
	}
}
