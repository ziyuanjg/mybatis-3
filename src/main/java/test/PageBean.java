package test;

import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;

public class PageBean {

	//当前页数
	private int pageNumber;
	//每页条数
	private int pageSize;
	//总条数
	private int totalNumber;
	//总页数
	private int totalPageNumber;
	
	private MetaObject object;
	
	public PageBean(Object object) {
		this.object = MetaObject.forObject(object, new DefaultObjectFactory(), new DefaultObjectWrapperFactory(), new DefaultReflectorFactory());
	}

	public int getPageNumber() {
		return pageNumber;
	}

	public void setPageNumber(int pageNumber) {
		this.pageNumber = pageNumber;
	}

	public int getPageSize() {
		return pageSize;
	}

	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}

	public MetaObject getObject() {
		return object;
	}

	public void setObject(MetaObject object) {
		this.object = object;
	}
	

	
}
