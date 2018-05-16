package dao;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

import core.BookBasicInfo;
import core.Chapter;

import static Config.config.*;

public class DbControl {
	private static Connection con = null;
	
	private void OpenConnection() throws SQLException
	{
		con = DriverManager.getConnection(dburl, username, password);
	}
	
	private void CloseConnection() throws SQLException
	{
		con.close();
	}
	
	//用于更新书籍，如果书籍不存在则存储并缓存所有章节。如果已有部分章节，则只缓存新增章节
	public void AddBook(BookBasicInfo bookinfo, CopyOnWriteArrayList<Chapter> chapters)
	{		
		CallableStatement cs = null;
		PreparedStatement ps = null;
		
		if(chapters == null || chapters.size() == 0) return;
		
		try {
			this.OpenConnection();
			cs = con.prepareCall("call insert_bookinfo(?,?,?,?,?,?)");
			cs.setString(1, bookinfo.getBookName());
			cs.setString(2, bookinfo.getAuthor());
			cs.setString(3, bookinfo.getLastChapter());
			cs.setInt(4, bookinfo.isIsfinal()?1:0);
			cs.registerOutParameter(5, Types.INTEGER);
			cs.registerOutParameter(6, Types.INTEGER);
			cs.execute();
			int bookid = cs.getInt(5);
			int chapterid = cs.getInt(6);
			
			con.setAutoCommit(false);
			ps = con.prepareStatement("insert into chapters values(?,?,?,?)");
			for(Chapter chapter : chapters)
			{
				if(chapter.getId() <= chapterid) continue;
				ps.setString(1, chapter.getTitle());
				ps.setString(2, chapter.getText());
				ps.setInt(3, bookid);
				ps.setInt(4, chapter.getId());
				ps.executeUpdate();
			}
			con.commit();
			con.setAutoCommit(true);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally
		{
			try {
				if (cs != null) cs.close();
				if (ps != null) ps.close();
				if (con != null) this.CloseConnection();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}		
		}
	}
	
	//查询书籍的最新章节信息，如果没有书籍，则返回-1或0
	public int queryBookInfo(BookBasicInfo bookinfo)
	{
		CallableStatement cs = null;
		int id = 0;
		
		try {
			this.OpenConnection();
			cs = con.prepareCall("call query_bookinfo(?,?,?)");
			cs.setString(1, bookinfo.getBookName());
			cs.setString(2, bookinfo.getAuthor());
			cs.registerOutParameter(3, Types.INTEGER);
			cs.execute();
			id = cs.getInt(3);
			return id;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return -1;
		}
		finally
		{
			try {
				if (cs != null) cs.close();
				if (con != null) this.CloseConnection();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return -1;
			}		
		}
	}
	
	//获取当前缓存的章节内容
	public ArrayList<Chapter> getbookchapters(BookBasicInfo bookinfo)
	{
		PreparedStatement ps = null;
		ArrayList<Chapter> chapters = new ArrayList<Chapter>();
		try {
			this.OpenConnection();
			ps = con.prepareStatement("select bookid from books where bookname=? and author=?;");
			ps.setString(1, bookinfo.getBookName());
			ps.setString(2, bookinfo.getAuthor());
			ResultSet rs = ps.executeQuery();
			rs.last();
			int bookid = rs.getInt(1);
			ps.close();
			
			ps = con.prepareStatement("select chaptername,html from chapters where bookid=? order by chapterid;");
			ps.setInt(1, bookid);
			rs = ps.executeQuery();
			while(rs.next())
			{
				chapters.add(new Chapter(rs.getString(1), rs.getString(2)));
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		finally
		{
			try {
				if (ps != null) ps.close();
				if (con != null) this.CloseConnection();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}		
		}
		
		return chapters;
	}
}