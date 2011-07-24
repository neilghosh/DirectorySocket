package test.socket.directory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import util.XMLUtility;

public class DirectoryServer {

	private HashMap<String, ArrayList<test.socket.directory.Method>> serviceModel;

	public static void main(String args[]) {

		DirectoryServer directoryServer = new DirectoryServer();
		int portNumber = directoryServer.getPortNumber();
		//directoryServer.startSerever(portNumber);
		directoryServer.populateServiceModel();
		//directoryServer.processRequest("<request><header><type>invoke</type></header><body><service><serviceName>test.SampleClass</serviceName><Method><methodName>addNumbers</methodName><parameters><parameter>5</parameter><parameter>7</parameter></parameters></Method></service></body></request>");
		directoryServer.processRequest("<request><header><type>list</type></header><body></body></request>");
	
	}

	/*
	 * Takes the services model and generated the XML equivalent that can be
	 * transported via socket
	 */
	private Document generateXMLDirectoryResponse(
			HashMap<String, ArrayList<test.socket.directory.Method>> allMethods) {

		Document servicesDocument = XMLUtility.createDocument();
		Element servicesRoot = servicesDocument.createElement("services");
		servicesDocument.appendChild(servicesRoot);

		// Iterate through all the services. Each entry in the hashmap model
		// corresponds to one service
		for (String serviceName : allMethods.keySet()) {
			Element serviceNode = servicesDocument.createElement("service");
			servicesRoot.appendChild(serviceNode);

			Element serviceNameNode = servicesDocument
					.createElement("serviceName");
			Text serviceNameText = servicesDocument.createTextNode(serviceName);
			serviceNameNode.appendChild(serviceNameText);
			serviceNode.appendChild(serviceNameNode);

			Element methodsNode = servicesDocument.createElement("methods");
			serviceNode.appendChild(methodsNode);

			ArrayList<test.socket.directory.Method> methodList = new ArrayList<test.socket.directory.Method>();
			methodList = allMethods.get(serviceName);
			// iterate through the methods of the service
			for (test.socket.directory.Method method : methodList) {
				Element methodNode = servicesDocument.createElement("method");
				Element methodNameNode = servicesDocument
						.createElement("methodName");
				Text methodNameText = servicesDocument.createTextNode(method
						.getMethodName());
				methodNameNode.appendChild(methodNameText);
				methodNode.appendChild(methodNameNode);

				Element returnTypeNode = servicesDocument
						.createElement("returnType");
				Text returnTypeText = servicesDocument.createTextNode(method
						.getReturnType().toString());
				returnTypeNode.appendChild(returnTypeText);
				methodNode.appendChild(returnTypeNode);

				Element parametersNode = servicesDocument
						.createElement("parameters");

				methodsNode.appendChild(methodNode);
				ArrayList<String> parameterTypes = new ArrayList<String>();
				parameterTypes = method.getParameters();
				// Iterate through the parameterstypes of the methods
				for (String parameterType : parameterTypes) {
					Element parameterTypeNode = servicesDocument
							.createElement("parameterType");
					// add a text element to the child
					Text parameterTypeText = servicesDocument
							.createTextNode(parameterType.toString());
					parameterTypeNode.appendChild(parameterTypeText);
					parametersNode.appendChild(parameterTypeNode);
				}
				methodNode.appendChild(parametersNode);
			}
		}
		System.out.println(XMLUtility.xmlToString(servicesDocument));
		return servicesDocument;
	}

	private void startSerever(int portNumber) {
		ServerSocket calcServer = null;
		String DirRequestMsg;
		BufferedReader is;
		PrintStream os;
		Socket clientSocket = null;

		//Try to start a socket at the given port number
		try {
			calcServer = new ServerSocket(portNumber);
			System.out.println("Listining to port " + portNumber);
		} catch (IOException e) {
			System.out.println(e);
		}

		try {
			//Keep waiting for the client to connect 
			while (true) {
				clientSocket = calcServer.accept();
				System.out.println("Connected to "
						+ clientSocket.getRemoteSocketAddress());
				//is = new DataInputStream(clientSocket.getInputStream());
				is = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

				os = new PrintStream(clientSocket.getOutputStream());

				try {
					//Keep reading lines from the client socket
					while (((DirRequestMsg = is.readLine()) != null)) {

						System.out.println("Server recieved the expression "
								+ DirRequestMsg);

						String DireResponse = processRequest(DirRequestMsg);
						os.println(DireResponse);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				System.err.println("Closing connection with client");
				os.close();
				is.close();
				clientSocket.close();

			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/*
	 * This is invoked when message from the client has arrived and we need to process 
	 */
	private String processRequest(String dirRequestMsg) {
		Document responseDoc = null;

		String requestType = getRequestType(dirRequestMsg);
		//if request is a UDDI query 
		if (requestType.equalsIgnoreCase("list")) {
			responseDoc = warapResponse("list",
					generateXMLDirectoryResponse(populateServiceModel()));

		} else if (requestType.equalsIgnoreCase("invoke")) { //if the request is for remote procedure invokation 
			responseDoc = processServiceInvocation(dirRequestMsg);
			//responseDoc = generateXMLresponse(obj);
		}
		//if the type of the request is unknown
		else {
			responseDoc = warapResponse("error", "Invalid Command");

		}
		String DireResponse = XMLUtility.xmlToString(responseDoc);
		System.out.println("Generated Response" + XMLUtility.formatXML(DireResponse ));
		return DireResponse;
	}

	//Takes the invocation result Object and wraps it with response envelope ater converting to string
	private Document generateXMLresponse(Object obj) {

		Double d = (Double) obj;
		return warapResponse("result", d.toString());

	}

	/*
	 * Given the request XML , determines the service, method and parameters 
	 */
	private Document processServiceInvocation(String dirRequestMsg) {

		// parse the client request XML
		Document requestXML = XMLUtility.StringToXML(dirRequestMsg);
		String serviceName = requestXML.getElementsByTagName("serviceName")
				.item(0).getTextContent();
		String methodName = requestXML.getElementsByTagName("methodName")
				.item(0).getTextContent();
		NodeList parametersNode = requestXML.getElementsByTagName("parameter");
		ArrayList<String> parameters = new ArrayList<String>();

		for (int paramCounter = 0; paramCounter < parametersNode.getLength(); paramCounter++) {
			String parameter = parametersNode.item(paramCounter)
					.getTextContent();
			parameters.add(parameter);
		}

		Document retValue = invokeMethod(serviceName, methodName, parameters);

		return retValue;
	}

	/*
	 * Given the service, method and parameters , uses reflection to invoke the method
	 */
	private Document invokeMethod(String serviceName, String methodName,
			ArrayList<String> parameters) {
		ArrayList<test.socket.directory.Method> methods = null;
		Object retval = null;
		try {
			

			// get the signature of the method
			methods = serviceModel.get(serviceName);
			if(methods == null ){
				return reportError("No Such Service");
			}
			test.socket.directory.Method targetMethod = null;
			//Search for the method in the repository 
			for (test.socket.directory.Method method : methods) {
				if (method.getMethodName().equalsIgnoreCase(methodName)) {
					targetMethod = method;
				}
			}
			if(targetMethod == null){
				return reportError("No Such Method");
			}
			ArrayList<String> parameterTypes = targetMethod.getParameters();
			if(parameterTypes.size() != parameters.size()){
				return reportError("Number of parameters does not match");
			}
			List<Class<?>> paramTypeList = new ArrayList<Class<?>>();
			List<Object> paramList = new ArrayList<Object>();
			int parameterCount = 0;
			//For each parameter get the DataType and convert them to parameter value
			for (String parameter : parameters) {
				String parameterType = parameterTypes.get(parameterCount);
				Class<?> paramClass = getClass(parameterType);
				paramTypeList.add(paramClass);
				Object parameterValue = getValue(parameter, parameterType);
				paramList.add(parameterValue);
			}
			Class<?> cls = Class.forName(serviceName);
			Method meth = cls.getMethod(methodName,
					paramTypeList.toArray(new Class[paramTypeList.size()]));
			Object obj = cls.newInstance();
			retval = meth.invoke(obj, paramList.toArray());
			// String returnType=targetMethod.getReturnType();
			// Class returnTypeClass = getClass(returnType);
			// Object retVal = returnTypeClass.cast(returnTypeClass);

		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		}

		
		return generateXMLresponse(retval);

	}

	/*
	 * This returns the Class for a given data Type as String
	 */
	private Class<?> getClass(String parameterType) {

		if (parameterType.equalsIgnoreCase("double"))
			return double.class;
		else
			return null;

	}

	/*
	 * Returns the object after converting String to a given dataType
	 */
	private Object getValue(String parameter, String parameterType) {
		if (parameterType.equalsIgnoreCase("double"))
			return Double.parseDouble(parameter);
		else
			return null;
	}

	/*
	 * Determines the request Type from the client 
	 * 1. "list" - for directory service
	 * 2. "invoke" - for method invikation
	 */
	private String getRequestType(String dirRequestMsg) {
		Document responseDoc = XMLUtility.StringToXML(dirRequestMsg);
		// Determine the type of response
		String requestType = responseDoc.getElementsByTagName("type").item(0)
				.getTextContent();
		System.out.println("Request Type : " + requestType);
		return requestType;
	}

	/*
	 * Wraps any message in the envelope. Used for sending result/error to the client 
	 */
	private Document warapResponse(String responseType, String message) {
		String respString = "<response><header><type>" + responseType
				+ "</type></header><body><message>" + message
				+ "</message></body></response>";
		return XMLUtility.StringToXML(respString);
	}

	private Document reportError(String message){
		return warapResponse("error",message);
	}
	/*
	 * Wraps the directory service listing response into envelopse 
	 */
	private Document warapResponse(String responseType,
			Document XMLDirectoryResponse) {

		Document doc = XMLUtility.createDocument();
		Element root = doc.createElement("response");
		doc.appendChild(root);
		Element header = doc.createElement("header");
		root.appendChild(header);
		Element type = doc.createElement("type");
		Text typeText = doc.createTextNode(responseType);
		type.appendChild(typeText);
		header.appendChild(type);
		Element body = doc.createElement("body");
		body.appendChild(doc.importNode(
				XMLDirectoryResponse.getDocumentElement(), true));
		root.appendChild(body);

		return doc;
	}

	/*
	 * Gets the port number from teh config file
	 */
	private int getPortNumber() {
		Properties prop = new Properties();
		String fileName = "ServerConfig.properties";
		System.out.print(File.separatorChar);
		InputStream is = this.getClass().getClassLoader()
				.getResourceAsStream(fileName);

		try {
			prop.load(is);
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println(prop.getProperty("socket.port"));
		return Integer.parseInt(prop.getProperty("socket.port"));

	}

	/*
	 * This populates the model containing all the services and their method along with the parameter types
	 */
	private HashMap<String, ArrayList<test.socket.directory.Method>> populateServiceModel() {
		HashMap<String, ArrayList<test.socket.directory.Method>> methodMap = new HashMap<String, ArrayList<test.socket.directory.Method>>();
		for (String serviceClass : readDeployedClasses()) {
			methodMap.put(serviceClass, getMethods(serviceClass));
		}
		this.serviceModel = methodMap;
		return methodMap;
	}

	/*
	 * Takes the service name (class name) and returns all the methods along
	 * with the parameters and return types
	 */
	private ArrayList<test.socket.directory.Method> getMethods(String className) {

		//className = "test.SampleClass";
		Class<?> serviceClass = null;
		try {
			serviceClass = Class.forName(className);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		// Get the methods
		Method[] methods = serviceClass.getDeclaredMethods();
		ArrayList<test.socket.directory.Method> methodList = new ArrayList<test.socket.directory.Method>();
		// Loop through the methods and print out their names
		for (Method method : methods) {
			test.socket.directory.Method methodObj = new test.socket.directory.Method();
			// System.out.println("Method:" + method.getName());
			methodObj.setMethodName(method.getName());
			// System.out.println("Return:" + method.getReturnType());
			methodObj.setReturnType(method.getReturnType().toString());
			ArrayList<String> paramList = new ArrayList<String>();
			Class<?>[] paramTypes = method.getParameterTypes();
			for (Class<?> paramType : paramTypes) {
				// System.out.println("param:" + paramType.toString());
				paramList.add(paramType.toString());

			}
			methodObj.setParameters(paramList);
			methodList.add(methodObj);
		}
		return methodList;
	}

	/*
	 * Reads the classes deploed in the configuration file deploy.xml
	 */
	private ArrayList<String> readDeployedClasses() {
		ArrayList<String> classList = new ArrayList<String>();
		String pathSeparator = File.separator;

		//Document doc = XMLUtility.readXMLFile("config"+pathSeparator+"deploy.xml");
		Document doc = XMLUtility.readXMLFile(this.getClass().getClassLoader().getResourceAsStream("deploy.xml"));

		doc.getDocumentElement().normalize();
		Element rootElement = doc.getDocumentElement();
		NodeList classNodeList = rootElement.getChildNodes();

		for (int temp = 0; temp < classNodeList.getLength(); temp++) {
			Node classNode = classNodeList.item(temp);
			if (classNode.getNodeType() == Node.ELEMENT_NODE) {
				classList.add(classNode.getTextContent());
				System.out.println(classNode.getTextContent());
			}
		}
		return classList;
	}

}