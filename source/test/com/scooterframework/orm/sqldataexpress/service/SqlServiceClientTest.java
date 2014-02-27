/*
 *   This software is distributed under the terms of the FSF 
 *   Gnu Lesser General Public License (see lgpl.txt). 
 *
 *   This program is distributed WITHOUT ANY WARRANTY. See the
 *   GNU General Public License for more details.
 */
package com.scooterframework.orm.sqldataexpress.service;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.scooterframework.orm.sqldataexpress.object.RowData;
import com.scooterframework.orm.sqldataexpress.object.TableData;
import com.scooterframework.test.ScooterTestHelper;

/**
 * SqlServiceClientTest class
 * 
 * @author (Fei) John Chen
 *
 */
public class SqlServiceClientTest extends ScooterTestHelper {
	
	@Test
	public void test_retrieveTableDataBySQL() {
		String sql = "SELECT * FROM pets ORDER BY birth_date DESC";
		TableData td = SqlServiceClient.retrieveTableDataBySQL(sql);
		assertEquals("total vets", 13, td.getTableSize());
		
		RowData rd1 = td.getFirstRow();
		assertEquals("first row pet name", "Basil", rd1.getField("name"));
	}
	
	@Test
	public void test_retrieveTableDataBySQL_inputs() {
		String sql = "SELECT * FROM pets WHERE name = ?name ORDER BY birth_date DESC";
		Map<String, Object> inputs = new HashMap<String, Object>();
		inputs.put("name", "Max");
		TableData td = SqlServiceClient.retrieveTableDataBySQL(sql, inputs);
		assertEquals("total vets", 1, td.getTableSize());
		
		RowData rd1 = td.getFirstRow();
		assertEquals("first row pet id", "8", rd1.getField("id").toString());
	}
	
	@Test
	public void test_retrieveTableDataBySQLKey() {
		String sql = "getAllPets";
		TableData td = SqlServiceClient.retrieveTableDataBySQLKey(sql);
		assertEquals("total vets", 13, td.getTableSize());
		
		RowData rd1 = td.getFirstRow();
		assertEquals("first row pet name", "Leo", rd1.getField("name"));
	}
	
	@Test
	public void test_retrieveTableDataBySQLKey_inputs() {
		String sql = "getPetByName";
		Map<String, Object> inputs = new HashMap<String, Object>();
		inputs.put("name", "Max");
		TableData td = SqlServiceClient.retrieveTableDataBySQLKey(sql, inputs);
		assertEquals("total vets", 1, td.getTableSize());
		
		RowData rd1 = td.getFirstRow();
		assertEquals("first row pet id", "8", rd1.getField("id").toString());
	}
	
	@Test
	public void test_retrieveRowsBySQL() {
		String sql = "SELECT * FROM pets ORDER BY birth_date DESC";
		List<RowData> rows = SqlServiceClient.retrieveRowsBySQL(sql);
		assertEquals("total rows", 13, rows.size());
		
		RowData rd1 = (RowData)rows.get(0);
		assertEquals("first row pet name", "Basil", rd1.getField("name"));
	}
	
	@Test
	public void test_retrieveRowsBySQL_inputs() {
		String sql = "SELECT * FROM pets WHERE name = ?name ORDER BY birth_date DESC";
		Map<String, Object> inputs = new HashMap<String, Object>();
		inputs.put("name", "Max");
		List<RowData> rows = SqlServiceClient.retrieveRowsBySQL(sql, inputs);
		assertEquals("total rows", 1, rows.size());
		
		RowData rd1 = (RowData)rows.get(0);
		assertEquals("first row pet id", "8", rd1.getField("id").toString());
	}
	
	@Test
	public void test_retrieveObjectBySQL() {
		String sql = "SELECT name FROM pets WHERE id = 12";
		Object data = SqlServiceClient.retrieveObjectBySQL(sql);
		assertEquals("name of pet with id 12", "Lucky", data);
	}
	
	@Test
	public void test_retrieveObjectBySQL_inputs() {
		String sql = "SELECT name FROM pets WHERE id = ?1";
		Map<String, Object> inputs = new HashMap<String, Object>();
		inputs.put("1", "12");
		Object data = SqlServiceClient.retrieveObjectBySQL(sql, inputs);
		assertEquals("name of pet with id 12", "Lucky", data);
	}
	
	@Test
	public void test_executeSQL() {
		Object nextID = getNextPetID();
		
		String sql = "INSERT INTO pets (id, name, type_id, owner_id) VALUES (" + nextID + ", 'Cupid', 1, 10)";
		int insertCount = SqlServiceClient.executeSQL(sql);
		assertEquals("number of rows inserted", 1, insertCount);
		
		String sql2 = "SELECT name FROM pets WHERE name = 'Cupid'";
		Object data = SqlServiceClient.retrieveObjectBySQL(sql2);
		assertEquals("name of the new pet", "Cupid", data.toString());
		
		String sql3 = "DELETE FROM pets WHERE name = 'Cupid'";
		int deleteCount = SqlServiceClient.executeSQL(sql3);
		assertEquals("number of rows deleted", 1, deleteCount);
	}
	
	@Test
	public void test_executeSQL_inputs() {
		Object nextID = getNextPetID();
		
		String sql = "INSERT INTO pets (id, name, type_id, owner_id) VALUES (?id, ?name, 1, 10)";
		Map<String, Object> inputs = new HashMap<String, Object>();
		inputs.put("id", nextID);
		inputs.put("name", "Pingping");
		int insertCount = SqlServiceClient.executeSQL(sql, inputs);
		assertEquals("number of rows inserted", 1, insertCount);
		
		String sql2 = "SELECT name FROM pets WHERE name = 'Pingping'";
		Object data = SqlServiceClient.retrieveObjectBySQL(sql2);
		assertEquals("name of the new pet", "Pingping", data.toString());
		
		String sql3 = "DELETE FROM pets WHERE name = ?name";
		int deleteCount = SqlServiceClient.executeSQL(sql3, inputs);
		assertEquals("number of rows deleted", 1, deleteCount);
	}
	
	private Object getNextPetID() {
		String findNextID = "SELECT (max(id)+1) FROM pets";
		return SqlServiceClient.retrieveObjectBySQL(findNextID);
	}
}
