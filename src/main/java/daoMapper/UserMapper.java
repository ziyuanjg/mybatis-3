package daoMapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.cursor.Cursor;

import dao.User;
import test.PageBean;

public interface UserMapper {

	public User selectUser(Integer id);
	
	public String selectCity(int id);
	
	public int selectPolicy(int id);
	
	@Select("select phoneNO from users where id = #{id}")
	@Options(useCache=true)
	public String selectPhoneNO(int id);
	
	@Select("select id from users where phoneNO = #{phoneNO}")
	public int selectID(String phoneNO);
	
//	@Select("select id from users where phoneNO = #{phoneNO}")
	public int selectIDByUser(User user);
	
//	@Select("select id from users limit #{start},#{end}")
//	@ResultType(String.class)
	public List selectList(@Param("start") int start, @Param("end") int end);
	
	public String selectCityName(int id);
	
	@Update("update users set phoneNO = #{phoneNO} where id = #{id}")
	public int updatePhoneNO(@Param("id")int id, @Param("phoneNO")String phoneNO);
	
	@Select("select phoneNO from users limit 10")
	public Cursor<User> selectAAA();
	
//	@Insert("insert into users (phoneNO) values (#{phoneNO})")
	public void insertUser(User user);
}
