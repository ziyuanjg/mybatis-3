package test;

import java.io.Serializable;

public class InterceptorClass implements Serializable{

	public void say(String s){
		System.out.println("say():"+s);
	}
}
