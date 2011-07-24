package test.socket.directory;

import java.util.ArrayList;
import java.util.List;

public class Method {
	private String methodName;
	private String returnType;
	private ArrayList<String> Parameters;
	

	public void setMethodName(String methodName) {
		this.methodName = methodName;
	}

	public String getMethodName() {
		return methodName;
	}

	
	public void setReturnType(String returnType) {
		this.returnType = returnType;
	}

	public String getReturnType() {
		return returnType;
	}

	public void setParameters(ArrayList<String> parameters) {
		Parameters = parameters;
	}

	public ArrayList<String> getParameters() {
		return Parameters;
	}

}
